# INTENTIONAL_THIN_TESTS — methods exempt from the no-cheat-tests audit

**Currently empty** — there are no justified thin tests in this port.

The previous 16 entries were all non-`@Test` `public void` methods —
`@Override` interface implementations (`doFilter`, `setReadListener`,
`write`, WebSocket `onOpen`/`onMessage`/`onClose`/`onError` callbacks),
`AutoCloseable.close()` cleanup, and harness reset helpers. The auditor
mis-flagged them because its C#/.NET shape pattern (`public void Name(...)`)
also matches Java's `public void` and didn't require a test attribute. That
detector bug is fixed upstream — the auditor now only treats such a method as
a test when a `@Test`/`[Fact]`/`[Theory]`/etc. attribute precedes it, so
interface stubs and cleanup helpers are never flagged and need no entry here.

For a genuine thin `@Test` that must stay, prefer the in-code marker over a
`file:line` entry (markers ride with the code through reflow; line numbers
drift):

```java
@Test
public void smokeConstructor() {  // no-cheat: smoke test — exercises the build path only
    new Thing();
}
```

Format if a `file:line` entry is ever needed: `- <file:line> — <justification>`.
