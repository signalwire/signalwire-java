package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.PlayBackgroundFileSkill;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlayBackgroundSkillTest {

    @Test
    void testSkillProperties() {
        PlayBackgroundFileSkill skill = new PlayBackgroundFileSkill();
        assertEquals("play_background_file", skill.getName());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupFailsWithoutFiles() {
        assertFalse(new PlayBackgroundFileSkill().setup(Map.of()));
    }

    @Test
    void testSetupFailsWithEmptyFiles() {
        assertFalse(new PlayBackgroundFileSkill().setup(Map.of("files", List.of())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetupSucceedsWithFiles() {
        List<Map<String, Object>> files = List.of(
                Map.of("key", "hold_music", "url", "https://example.com/music.mp3", "description", "Hold music")
        );
        assertTrue(new PlayBackgroundFileSkill().setup(Map.of("files", files)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwaigFunctionsGenerated() {
        PlayBackgroundFileSkill skill = new PlayBackgroundFileSkill();
        List<Map<String, Object>> files = List.of(
                Map.of("key", "music", "url", "https://example.com/music.mp3", "description", "Music")
        );
        skill.setup(Map.of("files", files));
        var fns = skill.getSwaigFunctions();
        assertEquals(1, fns.size());
        assertEquals("play_background_file", fns.get(0).get("function"));
        assertTrue(fns.get(0).containsKey("data_map"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCustomToolName() {
        PlayBackgroundFileSkill skill = new PlayBackgroundFileSkill();
        List<Map<String, Object>> files = List.of(
                Map.of("key", "music", "url", "https://example.com/m.mp3", "description", "Music")
        );
        skill.setup(Map.of("files", files, "tool_name", "bg_player"));
        assertEquals("bg_player", skill.getSwaigFunctions().get(0).get("function"));
    }

    @Test
    void testRegisterToolsEmpty() {
        PlayBackgroundFileSkill skill = new PlayBackgroundFileSkill();
        List<Map<String, Object>> files = List.of(
                Map.of("key", "k", "url", "u", "description", "d")
        );
        skill.setup(Map.of("files", files));
        assertTrue(skill.registerTools().isEmpty());
    }
}
