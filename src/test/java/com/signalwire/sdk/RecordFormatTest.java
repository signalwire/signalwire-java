package com.signalwire.sdk;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.RecordFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the typed {@link RecordFormat} enum and the equivalent bare string
 * produce the IDENTICAL record-call payload — both through the SWAIG
 * {@link FunctionResult#recordCall} action and through the AgentBase builder's
 * {@code recordFormat} setter. No mocks: real FunctionResult / real AgentBase
 * rendering the real SWML.
 */
class RecordFormatTest {

    @Test
    void enumValueIsTheCanonicalWireString() {
        assertEquals("mp3", RecordFormat.MP3.getValue());
        assertEquals("wav", RecordFormat.WAV.getValue());
        assertEquals("mp4", RecordFormat.MP4.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void functionResultRecordCallEnumEqualsString() {
        // Build the record_call action both ways: typed enum vs bare string.
        FunctionResult viaEnum = new FunctionResult("Recording")
                .recordCall("ctrl-1", true, RecordFormat.MP3, "both");
        FunctionResult viaString = new FunctionResult("Recording")
                .recordCall("ctrl-1", true, "mp3", "both");

        // The entire serialized result must be byte-for-byte identical.
        assertEquals(viaString.toMap(), viaEnum.toMap());

        // And the format that actually lands in the SWML record_call verb is
        // the canonical wire string "mp3" (not the enum's name "MP3").
        Map<String, Object> swml = (Map<String, Object>) viaEnum.getActions().get(0).get("SWML");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        Map<String, Object> recordParams = (Map<String, Object>) main.get(0).get("record_call");
        assertEquals("mp3", recordParams.get("format"));
        assertEquals("both", recordParams.get("direction"));
        assertEquals(true, recordParams.get("stereo"));
        assertEquals("ctrl-1", recordParams.get("control_id"));
    }

    @Test
    void functionResultNullFormatEnumMatchesStringDefault() {
        // A null RecordFormat must behave exactly like a null String (the
        // verb-level default "wav" is applied identically).
        FunctionResult viaEnum = new FunctionResult("Recording")
                .recordCall(null, false, (RecordFormat) null, "both");
        FunctionResult viaString = new FunctionResult("Recording")
                .recordCall(null, false, (String) null, "both");
        assertEquals(viaString.toMap(), viaEnum.toMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentBuilderRecordFormatEnumEqualsString() {
        // The builder setter via the enum renders the SAME SWML record_call
        // format as the bare string — through the real AgentBase render path.
        AgentBase viaEnum = AgentBase.builder()
                .name("rec-enum").authUser("u").authPassword("p")
                .recordCall(true).recordFormat(RecordFormat.WAV)
                .build();
        AgentBase viaString = AgentBase.builder()
                .name("rec-string").authUser("u").authPassword("p")
                .recordCall(true).recordFormat("wav")
                .build();

        String fmtEnum = renderedRecordFormat(viaEnum);
        String fmtString = renderedRecordFormat(viaString);
        assertEquals("wav", fmtString);   // string path
        assertEquals("wav", fmtEnum);     // enum path — identical
        assertEquals(fmtString, fmtEnum);
    }

    @SuppressWarnings("unchecked")
    private static String renderedRecordFormat(AgentBase agent) {
        Map<String, Object> swml = agent.renderSwml("http://example.test");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        for (Map<String, Object> verb : main) {
            if (verb.containsKey("record_call")) {
                Map<String, Object> recParams = (Map<String, Object>) verb.get("record_call");
                return (String) recParams.get("format");
            }
        }
        return null;
    }
}
