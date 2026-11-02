package dev.qingframe
import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
class WorkspacePolicyTest {
 @Test fun fullWindowThresholds(){for((w,h) in listOf(425f to 623f,608f to 860f,400f to 660f,600f to 1200f))assertTrue(isWideWorkspace(w,h));for((w,h)in listOf(360f to 800f,412f to 915f,412f to 732f,480f to 854f,399f to 623f,400f to 661f))assertFalse(isWideWorkspace(w,h))}
 @Test fun asymmetricActualCoverIsSafe(){val s=workspaceSafety(622.545f,57.818f,0f,WorkspaceCorner(10.182f,10.182f,10.182f),WorkspaceCorner(56f,368.727f,56f),WorkspaceCorner(10.182f,10.182f,612.363f),WorkspaceCorner(56f,368.727f,566.545f),424.727f);assertEquals(60f,s.top,0f);assertEquals(28f,s.bottom,0f);assertTrue(s.navRight>=17f);assertEquals(12f,s.headerRight,0f)}
}
