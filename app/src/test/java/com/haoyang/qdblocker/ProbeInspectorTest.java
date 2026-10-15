package com.haoyang.qdblocker;

import com.haoyang.qdblocker.hook.ProbeInspector;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeInspectorTest {
    static class User { String UserId = "456"; String token = "SECRET_TOKEN"; String name = "PRIVATE_NAME"; }
    static class Comment { long ReviewId = 123; User user = new User(); String Content = "PRIVATE_BODY"; String Cookie = "SECRET_COOKIE"; }
    @Test public void includesOnlyNumericCandidateIdsAndFieldTypes() {
        Comment comment = new Comment();
        String ids = ProbeInspector.candidateIds(comment);
        assertTrue(ids.contains("ReviewId=123")); assertTrue(ids.contains("user.UserId=456"));
        assertFalse(ids.contains("PRIVATE")); assertFalse(ids.contains("SECRET"));
        String schema = ProbeInspector.schema(comment);
        assertTrue(schema.contains("Content:String")); assertFalse(schema.contains("PRIVATE_BODY"));
    }
    @Test public void rejectsNonNumericValuesAndBoundsCycles() {
        class Loop { Object user = this; String UserId = "not-an-id"; }
        assertEquals("none (mapping unverified)", ProbeInspector.candidateIds(new Loop()));
        assertEquals("null", ProbeInspector.schema(null));
    }
    @Test public void nameMatchingIsExactAndIgnoresCommentBody() {
        class Named { String NickName = "测试用户"; String Content = "测试用户"; }
        Named comment = new Named();
        assertTrue(ProbeInspector.matchesExactName(comment, "测试用户"));
        assertFalse(ProbeInspector.matchesExactName(comment, "看到"));
        comment.NickName = "其他用户";
        assertFalse(ProbeInspector.matchesExactName(comment, "测试用户"));
        assertFalse(ProbeInspector.matchesExactName(comment, ""));
    }
}
