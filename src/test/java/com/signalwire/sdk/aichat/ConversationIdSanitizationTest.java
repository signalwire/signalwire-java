/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Port of {@code test_handoff.py::TestConversationIdSanitization}: the service strips disallowed
 * characters from a conversation id silently, so {@link AIChatClient#createConversation} warns with
 * the id the service will actually store. Asserted on the computed stored-as id, which is the fact
 * the warning carries.
 */
class ConversationIdSanitizationTest {

  @ParameterizedTest
  @ValueSource(strings = {"conv-abc", "root.2", "a_b-c.d:e"})
  void safeIdsAreQuiet(String safe) {
    assertNull(AIChatClient.idAlteredTo(safe));
  }

  @ParameterizedTest
  @CsvSource({"root~2,root2", "conv id,convid", "x!,x"})
  void unsafeIdsWarnWithWhatWillActuallyBeStored(String unsafe, String storedAs) {
    assertEquals(storedAs, AIChatClient.idAlteredTo(unsafe));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void junkIsIgnoredRatherThanWarnedAbout(String junk) {
    assertNull(AIChatClient.idAlteredTo(junk));
  }
}
