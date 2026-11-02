"""Optional local-only reference cache/contact sheet. Pillow is a QA dependency."""
from io import BytesIO
from pathlib import Path
from .research import fetch, digest, save


def download(research, output, limit=12):
    from PIL import Image, ImageDraw
    root = Path(output)
    root.mkdir(parents=True, exist_ok=False)
    rows, thumbs = [], []
    Image.MAX_IMAGE_PIXELS = 25_000_000
    for index, item in enumerate(research['images'][:limit]):
        row = dict(item)
        try:
            data, mime, final = fetch(item['assetUrl'])
            if mime not in ('image/png','image/jpeg','image/webp','image/avif'):
                raise ValueError('Unsupported image MIME')
            with Image.open(BytesIO(data)) as image:
                image.load()
                if image.width*image.height > 25_000_000:
                    raise ValueError('Decoded image exceeds budget')
                row.update(width=image.width,height=image.height,sha256=digest(data),finalUrl=final,mime=mime)
                image.thumbnail((300,240))
                tile=Image.new('RGB',(320,280),'#eeeeee')
                tile.paste(image.convert('RGB'),((320-image.width)//2,0))
                ImageDraw.Draw(tile).text((8,250),f'{index}: unknown / review required',fill='black')
                thumbs.append(tile)
            path=root/(digest(data)+'.reference')
            path.write_bytes(data)
            row['localPath']=path.name
        except Exception as e:
            row['error']=str(e)
        rows.append(row)
    if thumbs:
        sheet=Image.new('RGB',(960,280*((len(thumbs)+2)//3)),'white')
        for i,tile in enumerate(thumbs):sheet.paste(tile,((i%3)*320,(i//3)*280))
        sheet.save(root/'contact-sheet.png')
    save(root/'image-index.json',rows)
    return rows


def review(index, annotations):
    """Apply agent visual observations only to exact downloaded bytes."""
    allowed_views={'front_folded','front_unfolded','front','back','side','detail','decoration','unknown'}
    by_hash={x.get('sha256'):x for x in index if x.get('sha256')}
    result=[]
    for sha, observation in annotations.items():
        if sha not in by_hash or observation.get('view') not in allowed_views:
            raise ValueError('Review must reference a downloaded hash and supported view')
        confidence=observation.get('confidenceScore')
        if type(confidence) not in (int,float) or not 0 <= confidence <= 1 or not observation.get('notes'):
            raise ValueError('Visual observation needs confidence and method/ambiguity notes')
        row=dict(by_hash[sha])
        row.update({k:observation[k] for k in ('view','confidenceScore','notes')})
        row.update(classification='visual_observation', usage='local_visual_reference_only',
                   redistributionPermission='not_verified')
        result.append(row)
    return dict(images=result, coverage=sorted({x['view'] for x in result}),
                limitations='Visual observations do not establish official dimensions, materials or redistribution rights')
