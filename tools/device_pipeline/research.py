"""Bounded public-resource collection. Never execute vendor JS or package images."""
import hashlib
import html
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlsplit, urldefrag
from urllib.request import Request, build_opener, HTTPRedirectHandler

HOSTS = ('mi.com', 'mi-img.com', 'mi-fds.com')
MAX_BYTES = 8 * 1024 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


def save(path, data):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def allowed(url):
    p = urlsplit(url)
    host = (p.hostname or '').lower()
    return (p.scheme == 'https' and not p.username and not p.password
            and p.port in (None, 443)
            and any(host == h or host.endswith('.' + h) for h in HOSTS)
            and not any(x in host.split('.') for x in ('account', 'community', 'bbs')))


class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not allowed(newurl):
            raise ValueError('Redirect outside public Xiaomi allowlist')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch(url):
    if not allowed(url):
        raise ValueError('Only public HTTPS Xiaomi product resources allowed')
    req = Request(url, headers={'User-Agent': 'Q-screenshot-research/1.0',
                               'Referer': 'https://www.mi.com/'})
    with build_opener(SafeRedirect).open(req, timeout=20) as response:
        data = response.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise ValueError('Resource exceeds 8 MiB limit')
        return data, response.headers.get_content_type(), response.geturl()


def normalize(text):
    text = re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m[1], 16)), text)
    return html.unescape(text.replace('\\/', '/'))


def links(text, base):
    """HTML/srcset/lazy attributes, CSS url(), JSON/JS string URLs; no JS eval."""
    text = normalize(text)
    found = re.findall(r'''["']([^"'<>\s]+)["']''', text)
    found += re.findall(r'url\(\s*([^\s)]+)\s*\)', text)
    found += re.findall(r'https://[^\s"\'<>),]+', text)
    # srcset first and subsequent candidates, including lazy srcset.
    for match in re.findall(r'''(?:srcset|data-srcset)\s*=\s*["']([^"']+)["']''', text):
        found.extend(x.strip().split()[0] for x in match.split(',') if x.strip())
    # Recognize only Xiaomi's literal prod base + literal imageFDSPath + literal
    # /images/name expression. Never evaluate JS, guess hashes, or mix base variants.
    binding = re.search(r'imgPath:([A-Za-z_$][\w$]*)\.prod', text)
    folders = set(re.findall(r'imageFDSPath:["\']([^"\']+)["\']', text))
    if binding and len(folders) == 1:
        block = re.search(r'(?:const|let|var)\s+' + re.escape(binding[1]) + r'=\{([^}]+)\}', text)
        prod = re.search(r'prod:["\'](https://[^"\']+)["\']', block[1]) if block else None
        if prod and allowed(prod[1]):
            folder = next(iter(folders))
            for suffix in re.findall(r'imageFDSPath\+["\'](/images/[^"\']+)["\']', text):
                found.append(prod[1].rstrip('/')+'/'+folder+suffix)
    result = []
    for value in found:
        value = value.strip('"\'')
        if re.search(r'\.(?:js|html|png|jpe?g|webp|avif)(?:[?#]|$)', value) or '/prod/' in value:
            url = urldefrag(urljoin(base, value))[0]
            if allowed(url) and url not in result:
                result.append(url)
    return result


def extract(text, source_id, aliases, state=None, variant=None):
    """Conservative candidates; scope comes from reviewed input, never nearest numbers."""
    text = normalize(text)
    plain = re.sub('<[^>]+>', ' ', text)
    compact = lambda x: re.sub(r'[\s_-]+', '', x).casefold()
    identity = any(compact(a) in compact(plain) for a in aliases)
    patterns = {
        'width_mm': r'(?:宽度|Width)\s*[：:"\s,]*([0-9]+(?:\.[0-9]+)?)\s*(?:mm|毫米)',
        'height_mm': r'(?:长度|高度|Height|Length)\s*[：:"\s,]*([0-9]+(?:\.[0-9]+)?)\s*(?:mm|毫米)',
        'thickness_mm': r'(?:厚度|Thickness|Depth)\s*[：:"\s,]*([0-9]+(?:\.[0-9]+)?)\s*(?:mm|毫米)',
        'diagonal_inches': r'(?:屏幕尺寸|屏幕大小|Screen size)\s*[：:"\s,]*([0-9]+(?:\.[0-9]+)?)\s*(?:英寸|inch|″)',
        'display_pixels': r'(?:分辨率|Resolution)\s*[：:"\s,]*([0-9]{3,4})\s*[×xX*]\s*([0-9]{3,4})',
    }
    candidates = []
    candidate_positions = {}
    for field, pattern in patterns.items():
        for m in re.finditer(pattern, plain, re.I):
            value = [int(m[1]), int(m[2])] if field == 'display_pixels' else float(m[1])
            candidate_positions[len(candidates)] = m.start()
            candidates.append(dict(field=field, value=value, sourceId=source_id,
                                   state=state, variant=variant, status='candidate',
                                   classification='unknown', identityMention=identity,
                                   evidence=plain[max(0, m.start()-60):m.end()+60]))
    # Bound each dimension to its own label. HTML stripping can place several
    # fields on one line; a following width/height/thickness starts a new scope.
    dimension_labels = re.compile(
        r'(宽度|Width|长度|高度|Height|Length|厚度|Thickness|Depth)[：:\s]+', re.I)
    labels = list(dimension_labels.finditer(plain))
    scoped_rows, replaced_positions = [], set()
    field_by_label = {'宽度': 'width_mm', 'width': 'width_mm',
                      '长度': 'height_mm', '高度': 'height_mm',
                      'height': 'height_mm', 'length': 'height_mm',
                      '厚度': 'thickness_mm', 'thickness': 'thickness_mm', 'depth': 'thickness_mm'}
    for index, label in enumerate(labels):
        end = labels[index + 1].start() if index + 1 < len(labels) else len(plain)
        segment = re.split(r'["\'<>;\n]', plain[label.end():end], maxsplit=1)[0][:100]
        parts = list(re.finditer(
            r'([0-9]+(?:\.[0-9]+)?)\s*(?:mm|毫米)\s*[（(](展开|闭合|合拢|folded|unfolded)[）)]',
            segment, re.I))
        if parts:
            replaced_positions.add(label.start())
            for part in parts:
                scoped_rows.append(dict(field=field_by_label[label[1].lower()],
                    value=float(part[1]), sourceId=source_id,
                    state='unfolded' if part[2].lower() in ('展开', 'unfolded') else 'folded',
                    variant=variant, status='candidate', classification='unknown',
                    identityMention=identity, evidence=plain[label.start():label.end()] + segment))
    candidates = [row for index, row in enumerate(candidates)
                  if candidate_positions[index] not in replaced_positions]
    candidates.extend(scoped_rows)
    for match in re.finditer(r'(外屏|内屏)\s+([0-9]+(?:\.[0-9]+)?)\s*英寸',plain):
        candidates.append(dict(field='diagonal_inches',value=float(match[2]),sourceId=source_id,
            state='folded' if match[1]=='外屏' else 'unfolded',variant=variant,status='candidate',
            classification='unknown',identityMention=identity,evidence=match[0]))
    colors = [m[1].strip() for m in re.finditer(r'(?:配色|颜色|Colors)\s*[：:]\s*([^<>;\n"{}]{1,120})',plain)]
    for match in re.finditer(r'colorName\(\)\{return\[([^]]+)\]',text):
        colors.extend(re.findall(r'["\']([^"\']+)["\']',match[1]))
    return candidates, colors, identity


def collect(config, output, offline=None, max_pages=12, max_images=20):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    aliases = config['aliases']
    if not aliases or not all(isinstance(x, str) and x.strip() for x in aliases):
        raise ValueError('Nonempty product aliases required')
    slug = config['deviceId']
    if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', slug):
        raise ValueError('Invalid deviceId')
    seeds = config.get('pages') or [{'url': f'https://www.mi.com/prod/{slug}{suffix}'} for suffix in ('/specs', '')]
    # Public mishop route probes follow the repository's documented product loader.
    # A successful response is still only a candidate until identity review.
    if not config.get('pages'):
        seeds += [{'url': f'https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/src/pages/{slug}/{suffix}index.html'} for suffix in ('specs/', '')]
    queue = [(x['url'], x, 0) for x in seeds]
    sources, candidates, images, colors, failures = [], [], [], [], []
    seen = set()
    offline_map = json.loads((Path(offline)/'index.json').read_text()) if offline else None
    while queue and len(seen) < max_pages:
        url, scope, depth = queue.pop(0)
        if url in seen:
            continue
        seen.add(url)
        try:
            if not allowed(url):
                raise ValueError('Untrusted source domain')
            if offline_map is not None:
                record = offline_map[url]
                local = (Path(offline)/record['file']).resolve()
                if not local.is_relative_to(Path(offline).resolve()):
                    raise ValueError('Offline path escapes snapshot')
                data, mime, final = local.read_bytes(), record['mime'], url
            else:
                data, mime, final = fetch(url)
                time.sleep(.2)
            if len(data) > MAX_BYTES or mime not in ('text/html', 'text/javascript', 'application/javascript', 'application/json', 'text/plain'):
                raise ValueError('Expected bounded HTML/JS/JSON text')
            text = data.decode('utf-8', errors='replace')
            if re.search(r'<title>[^<]*(?:captcha|access denied|登录|验证)', text, re.I):
                raise ValueError('Access wall; stop this path')
            sid = 'source-' + digest(url.encode())[:12]
            path = output/'cache'/f'{sid}.txt'
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(data)
            rows, color_rows, mention = extract(text, sid, aliases, scope.get('state'), scope.get('variant'))
            sources.append(dict(sourceId=sid, pageUrl=url, finalUrl=final, sha256=digest(data),
                                retrievedAt=datetime.now(timezone.utc).isoformat(), mime=mime,
                                localPath=str(path.relative_to(output)), identityMention=mention,
                                verifiedProductIdentity=False, usage='local_visual_reference_only',
                                redistributionPermission='not_verified', mode='offline_fixture' if offline else 'live'))
            candidates.extend(rows)
            colors.extend({'sourceId': sid, 'text': x, 'status': 'candidate'} for x in color_rows)
            for link in links(text, final):
                if re.search(r'\.(png|jpe?g|webp|avif)([?#]|$)', link, re.I):
                    if len(images) < 500 and not any(x['assetUrl'] == link for x in images):
                        # Only hints; unknown remains unknown until image inspection.
                        hints = [v for v in ('front', 'back', 'side', 'folded', 'unfolded') if re.search(r'(?<![a-z])'+v+r'(?![a-z])', link, re.I)]
                        images.append(dict(assetUrl=link, sourceId=sid, viewHints=hints,
                                           view='unknown', classification='unknown', productPathMatch=slug in link,
                                           usage='local_visual_reference_only', redistributionPermission='not_verified'))
                elif depth < 2 and (slug in link or 'prod-template' in link):
                    queue.append((link, {}, depth+1))
        except (OSError, ValueError, KeyError) as e:
            failures.append({'url': url, 'error': str(e)})
    images.sort(key=lambda x: not x['productPathMatch'])
    images=images[:max_images]
    research = dict(schemaVersion=1, deviceId=slug, formalName=aliases[0], sources=sources,
                    candidates=candidates, colors=colors, images=images, failures=failures,
                    searchQueries=[f'site:mi.com "{aliases[0]}" specs', f'site:mi.com "{aliases[0]}" 参数'],
                    limitations=['Unexecuted JS URL concatenation needs a public DOM/resource snapshot.',
                                 'Identity, state/variant scope and image views require agent review.',
                                 'Source candidates are not resolved official facts.'])
    save(output/'research.json', research)
    return research


def resolve(research, selections):
    """Reviewed evidence selection; numeric conflicts within selected scope block."""
    sources = {s['sourceId']: s for s in research['sources']}
    fields = {}
    for key, choice in selections.items():
        ids = choice['candidateIndices']
        if not all(type(i) is int and 0 <= i < len(research['candidates']) for i in ids):
            raise ValueError(f'{key}: invalid candidate index')
        rows = [research['candidates'][i] for i in ids]
        if not rows or len({json.dumps(r['value']) for r in rows}) != 1:
            raise ValueError(f'{key}: missing or conflicting selected values')
        if any(r['field'] != key.split('.')[-1] for r in rows):
            raise ValueError(f'{key}: selected field mismatch')
        if '.' in key and any(r.get('state') and r['state'] != key.split('.')[0] for r in rows):
            raise ValueError(f'{key}: selected state mismatch')
        if len({(r.get('state'),r.get('variant')) for r in rows}) != 1:
            raise ValueError(f'{key}: cannot merge different state/variant scopes')
        if any(sources[r['sourceId']].get('mode') == 'offline_fixture' for r in rows):
            raise ValueError(f'{key}: synthetic fixtures cannot establish official facts')
        if not choice.get('identityEvidence') or not choice.get('scopeEvidence'):
            raise ValueError(f'{key}: explicit identity and variant/state scope evidence required')
        if any(not sources[r['sourceId']]['identityMention'] for r in rows):
            raise ValueError(f'{key}: source lacks product identity')
        peers = [r for r in research['candidates'] if r['field'] == rows[0]['field'] and
                 r.get('state') == rows[0].get('state') and r.get('variant') == rows[0].get('variant')]
        conflicts = [r for r in peers if r['value'] != rows[0]['value']]
        if conflicts and not choice.get('conflictResolution'):
            raise ValueError(f'{key}: unresolved dimension/variant conflict')
        fields[key] = dict(value=rows[0]['value'], confidence='official',
                           sourceIds=[r['sourceId'] for r in rows], evidence=[r['evidence'] for r in rows],
                           identityEvidence=choice['identityEvidence'], scopeEvidence=choice['scopeEvidence'],
                           conflictResolution=choice.get('conflictResolution'), confidenceScore=0.9,
                           notes='Official reported number; not a measured CAD parameter')
    return dict(schemaVersion=1, deviceId=research['deviceId'], formalName=research['formalName'],
                fields=fields, sources=research['sources'], verificationStatus='partial_reconstruction')
