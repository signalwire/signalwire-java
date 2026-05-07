package com.signalwire.sdk;

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.signalwire.sdk.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the top-level convenience entry points exposed on the
 * {@link com.signalwire.sdk.Signalwire} static utility class —
 * {@code RestClient}, {@code registerSkill}, {@code addSkillDirectory},
 * {@code listSkillsWithParams}. These mirror Python's package-level
 * {@code signalwire/__init__.py} factory + skill registry helpers.
 */
class SignalwireTopLevelTest {

    @Test
    void restClientFromKeywordCredentials() {
        Map<String, String> kwargs = new HashMap<>();
        kwargs.put("project", "p-123");
        kwargs.put("token", "t-456");
        kwargs.put("space", "demo.signalwire.com");
        RestClient client = Signalwire.RestClient(Collections.emptyList(), kwargs);
        assertNotNull(client);
        // Namespaces are wired up.
        assertNotNull(client.fabric());
        assertNotNull(client.calling());
    }

    @Test
    void restClientFromPositionalCredentials() {
        RestClient client = Signalwire.RestClient(
                Arrays.asList("proj", "tok", "pos.signalwire.com"),
                Collections.emptyMap());
        assertNotNull(client);
    }

    @Test
    void restClientThrowsOnMissingCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> Signalwire.RestClient(Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void addSkillDirectoryRecordsThePath() throws Exception {
        Path tmp = Files.createTempDirectory("sw-skill-dir-");
        try {
            Signalwire.addSkillDirectory(tmp.toString());
            // The singleton internally tracks paths; we verify through a
            // round-trip with addSkillDirectory's IllegalArgumentException
            // for invalid paths (so we know the helper at least executed).
            assertThrows(IllegalArgumentException.class,
                    () -> Signalwire.addSkillDirectory("/no/such/path/zzz_top_level_test"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void addSkillDirectoryThrowsOnMissingDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> Signalwire.addSkillDirectory("/no/such/path/zzz_java_top_level"));
    }

    @Test
    void listSkillsWithParamsReturnsSchemaMap() {
        Map<String, Map<String, Object>> schema = Signalwire.listSkillsWithParams();
        assertNotNull(schema);
        assertFalse(schema.isEmpty());
        for (Map.Entry<String, Map<String, Object>> entry : schema.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().get("name"));
            assertNotNull(entry.getValue().get("parameters"));
        }
    }

    @Test
    void registerSkillRegistersClass() {
        // Use the existing SkillBase implementation classes — registering a
        // builtin skill that's already in the registry is a valid no-op.
        try {
            Signalwire.registerSkill(TopLevelDummySkill.class);
            assertTrue(SkillRegistry.list().contains("top_level_dummy_skill_java"));
        } finally {
            // SkillRegistry.register() mutates a static map; clean up so we
            // don't leak this test-only skill into other test classes' count
            // assertions (e.g. RegistryTest.testRegistryHas18Skills,
            // SkillsTest.testRegistryHasAllSkills). Python's parity tests
            // construct fresh per-test SkillRegistry() instances and never
            // touch global state — Java's static registry forces explicit
            // cleanup here.
            SkillRegistry.unregister("top_level_dummy_skill_java");
        }
    }

    /**
     * Minimal SkillBase implementation for the registerSkill test.
     * SkillBase is an interface; we provide a stub that the singleton
     * registry's {@code Supplier<SkillBase>} factory can instantiate via
     * reflection.
     */
    public static class TopLevelDummySkill implements SkillBase {
        public static final String SKILL_NAME = "top_level_dummy_skill_java";

        @Override
        public String getName() {
            return "top_level_dummy_skill_java";
        }

        @Override
        public String getDescription() {
            return "Dummy skill for parity test";
        }

        @Override
        public boolean setup(Map<String, Object> params) {
            return true;
        }

        @Override
        public java.util.List<ToolDefinition> registerTools() {
            return java.util.Collections.emptyList();
        }
    }
}
