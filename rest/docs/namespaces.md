# All Namespaces

Reference for every namespace beyond Fabric and Calling (which have their own pages). Every namespace is reached through a fluent accessor on the `RestClient` (e.g. `client.phoneNumbers()`, `client.video().rooms()`). CRUD methods take a `Map<String, Object>` body for create/update; some resources use a typed request builder (shown below).

<!-- snippet-setup -->
```java
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.namespaces.generated.*;

RestClient client = RestClient.builder().build();
```

## Phone Numbers

```java
// List your phone numbers
Map<String, Object> numbers = client.phoneNumbers().list();
numbers = client.phoneNumbers().list(Map.of("name", "Main"));

// Search available numbers to purchase
Map<String, Object> available = client.phoneNumbers().search(
    Map.of("area_code", "512", "number_type", "local"));

// Purchase a number
Map<String, Object> number = client.phoneNumbers().create(
    Map.of("number", "+15551234567"));

// Get / update / release
number = client.phoneNumbers().get("pn-uuid");
client.phoneNumbers().update("pn-uuid", Map.of("name", "Support Line"));
client.phoneNumbers().delete("pn-uuid");
```

## Addresses

```java
Map<String, Object> addresses = client.addresses().list(Map.of());
Map<String, Object> address = client.addresses().create(
    Addresses.CreateRequest.builder()
        .label("Office")
        .streetNumber("123")
        .streetName("Main St")
        .city("Austin")
        .state("TX")
        .build());
address = client.addresses().get("addr-uuid", Map.of());
client.addresses().delete("addr-uuid");
```

## Queues

```java
Map<String, Object> queues = client.queues().list();
Map<String, Object> queue = client.queues().create(Map.of("name", "Support"));
queue = client.queues().get("q-uuid");
client.queues().update("q-uuid", Map.of("name", "VIP Support"));
client.queues().delete("q-uuid");

// Members (the trailing map holds query parameters)
Map<String, Object> members = client.queues().listMembers("q-uuid", Map.of());
Map<String, Object> nextMember = client.queues().getNextMember("q-uuid", Map.of());
Map<String, Object> member = client.queues().getMember("q-uuid", "member-uuid", Map.of());
```

## Recordings

```java
Map<String, Object> recordings = client.recordings().list(Map.of());
Map<String, Object> recording = client.recordings().get("rec-uuid", Map.of());
client.recordings().delete("rec-uuid");
```

## Number Groups

```java
Map<String, Object> groups = client.numberGroups().list();
Map<String, Object> group = client.numberGroups().create(Map.of("name", "Marketing"));
group = client.numberGroups().get("ng-uuid");
client.numberGroups().update("ng-uuid", Map.of("name", "Sales"));
client.numberGroups().delete("ng-uuid");

// Memberships (addMembership takes a typed request builder)
Map<String, Object> memberships = client.numberGroups().listMemberships("ng-uuid", Map.of());
client.numberGroups().addMembership("ng-uuid",
    NumberGroups.AddMembershipRequest.builder().phoneNumberId("pn-uuid").build());
Map<String, Object> membership = client.numberGroups().getMembership("mem-uuid", Map.of());
client.numberGroups().deleteMembership("mem-uuid");
```

## Verified Caller IDs

```java
Map<String, Object> callers = client.verifiedCallers().list();
Map<String, Object> caller = client.verifiedCallers().create(
    Map.of("phone_number", "+15551234567", "name", "Office"));
caller = client.verifiedCallers().get("vc-uuid");
client.verifiedCallers().update("vc-uuid", Map.of("name", "Main Office"));
client.verifiedCallers().delete("vc-uuid");

// Verification flow (submitVerification takes a typed request builder)
client.verifiedCallers().redialVerification("vc-uuid");
client.verifiedCallers().submitVerification("vc-uuid",
    VerifiedCallers.SubmitVerificationRequest.builder().verificationCode("123456").build());
```

## SIP Profile

Singleton resource -- no ID needed. `update` takes a typed request builder:

```java
Map<String, Object> profile = client.sipProfile().get(Map.of());
client.sipProfile().update(
    SipProfile.UpdateRequest.builder().domainIdentifier("myproject").build());
```

## Phone Number Lookup

```java
Map<String, Object> info = client.lookup().phoneNumber("+15551234567", Map.of());
info = client.lookup().phoneNumber("+15551234567", Map.of("include", "carrier,cnam"));
```

Note: carrier and CNAM lookups are billable.

## Short Codes

```java
Map<String, Object> codes = client.shortCodes().list(Map.of());
Map<String, Object> code = client.shortCodes().get("sc-uuid", Map.of());
client.shortCodes().update("sc-uuid",
    ShortCodes.UpdateRequest.builder().name("Alerts").build());
```

## Imported Phone Numbers

`create` takes a typed request builder:

```java
client.importedNumbers().create(
    ImportedNumbers.CreateRequest.builder().number("+15559999999").build());
```

## MFA (Multi-Factor Authentication)

Each method takes a typed request builder:

```java
// Request a verification code via SMS
Map<String, Object> result = client.mfa().sms(
    Mfa.SmsRequest.builder()
        .to("+15551234567")
        .from("+15559876543")
        .message("Your code is {code}")
        .build());
String requestId = (String) result.get("id");

// Or via phone call
result = client.mfa().call(
    Mfa.CallRequest.builder()
        .to("+15551234567")
        .from("+15559876543")
        .build());

// Verify the code
result = client.mfa().verify(requestId,
    Mfa.VerifyRequest.builder().token("123456").build());
```

## 10DLC Campaign Registry

```java
// Brands
Map<String, Object> brands = client.registry().brands().list(Map.of());
Map<String, Object> brand = client.registry().brands().create(
    Map.of("name", "My Brand", "ein", "12-3456789"));
brand = client.registry().brands().get("brand-uuid", Map.of());

// Campaigns under a brand
Map<String, Object> campaigns = client.registry().brands().listCampaigns("brand-uuid", Map.of());
Map<String, Object> campaign = client.registry().brands().createCampaign(
    "brand-uuid", Map.of("description", "Alerts"));

// Campaign management (update takes a typed request builder)
campaign = client.registry().campaigns().get("camp-uuid", Map.of());
client.registry().campaigns().update("camp-uuid",
    RegistryCampaigns.UpdateRequest.builder().name("Updated alerts").build());

// Number assignments
Map<String, Object> numbers = client.registry().campaigns().listNumbers("camp-uuid", Map.of());
Map<String, Object> orders = client.registry().campaigns().listOrders("camp-uuid", Map.of());
Map<String, Object> order = client.registry().campaigns().createOrder("camp-uuid",
    RegistryCampaigns.CreateOrderRequest.builder()
        .phoneNumbers(List.of("pn-1"))
        .build());
order = client.registry().orders().get("order-uuid", Map.of());
client.registry().numbers().delete("number-assignment-uuid");
```

## Datasphere

```java
// Documents
Map<String, Object> docs = client.datasphere().documents().list();
Map<String, Object> doc = client.datasphere().documents().create(
    Map.of("url", "https://example.com/doc.pdf", "tags", List.of("support")));
doc = client.datasphere().documents().get("doc-uuid");
client.datasphere().documents().update("doc-uuid",
    Map.of("tags", List.of("support", "billing")));
client.datasphere().documents().delete("doc-uuid");

// Semantic search (typed request builder)
Map<String, Object> results = client.datasphere().documents().search(
    DatasphereDocuments.SearchRequest.builder()
        .queryString("How do I reset my password?")
        .tags(List.of("support"))
        .count(5L)
        .build());

// Chunks
Map<String, Object> chunks = client.datasphere().documents().listChunks("doc-uuid", Map.of());
Map<String, Object> chunk = client.datasphere().documents().getChunk("doc-uuid", "chunk-uuid", Map.of());
client.datasphere().documents().deleteChunk("doc-uuid", "chunk-uuid");
```

## Video

```java
// Rooms
Map<String, Object> rooms = client.video().rooms().list();
Map<String, Object> room = client.video().rooms().create(
    Map.of("name", "standup", "max_members", 10));
room = client.video().rooms().get("room-uuid");
client.video().rooms().update("room-uuid", Map.of("max_members", 20));
client.video().rooms().delete("room-uuid");
client.video().rooms().listStreams("room-uuid", Map.of());
client.video().rooms().createStream("room-uuid",
    VideoRooms.CreateStreamRequest.builder().url("rtmp://example.com/live").build());

// Room tokens
Map<String, Object> token = client.video().roomTokens().create(
    VideoRoomTokens.CreateRequest.builder()
        .roomName("standup")
        .userName("alice")
        .build());

// Room sessions
Map<String, Object> sessions = client.video().roomSessions().list(Map.of("room_name", "standup"));
Map<String, Object> session = client.video().roomSessions().get("session-uuid");
Map<String, Object> events = client.video().roomSessions().listEvents("session-uuid", Map.of());
Map<String, Object> members = client.video().roomSessions().listMembers("session-uuid", Map.of());
Map<String, Object> recordings = client.video().roomSessions().listRecordings("session-uuid", Map.of());

// Room recordings
Map<String, Object> recs = client.video().roomRecordings().list(Map.of());
Map<String, Object> rec = client.video().roomRecordings().get("rec-uuid", Map.of());
client.video().roomRecordings().delete("rec-uuid");

// Conferences
Map<String, Object> confs = client.video().conferences().list();
Map<String, Object> conf = client.video().conferences().create(
    Map.of("name", "all-hands", "quality", "720p"));
conf = client.video().conferences().get("conf-uuid");
client.video().conferences().update("conf-uuid", Map.of("quality", "1080p"));
client.video().conferences().delete("conf-uuid");
Map<String, Object> tokens = client.video().conferences().listConferenceTokens("conf-uuid", Map.of());
client.video().conferences().listStreams("conf-uuid", Map.of());
client.video().conferences().createStream("conf-uuid",
    VideoConferences.CreateStreamRequest.builder().url("rtmp://example.com/live").build());

// Conference tokens
Map<String, Object> conferenceToken = client.video().conferenceTokens().get("token-uuid", Map.of());
client.video().conferenceTokens().reset("token-uuid");

// Streams (update takes a typed request builder)
Map<String, Object> stream = client.video().streams().get("stream-uuid", Map.of());
client.video().streams().update("stream-uuid",
    VideoStreams.UpdateRequest.builder().url("rtmp://example.com/new").build());
client.video().streams().delete("stream-uuid");
```

## Logs

All log endpoints are read-only.

```java
// Message logs
Map<String, Object> logs = client.logs().messages().list(Map.of("include_deleted", "true"));
Map<String, Object> log = client.logs().messages().get("log-uuid");

// Voice logs (with events)
logs = client.logs().voice().list();
log = client.logs().voice().get("log-uuid");
Map<String, Object> events = client.logs().voice().listEvents("log-uuid", Map.of());

// Fax logs
logs = client.logs().fax().list();
log = client.logs().fax().get("log-uuid");

// Conference logs
logs = client.logs().conferences().list(Map.of());
```

## Project Tokens

Each mutating method takes a typed request builder:

```java
Map<String, Object> token = client.project().tokens().create(
    ProjectTokens.CreateRequest.builder()
        .name("ci-token")
        .permissions(List.of("calling", "messaging", "numbers"))
        .build());
client.project().tokens().update("token-uuid",
    ProjectTokens.UpdateRequest.builder().name("renamed-token").build());
client.project().tokens().delete("token-uuid");
```

## PubSub Tokens

`createToken` takes a typed request builder:

```java
Map<String, Object> token = client.pubsub().createToken(
    PubSub.CreateTokenRequest.builder()
        .ttl(60L)
        .channels(Map.of("updates", Map.of("read", true, "write", false)))
        .memberId("user-123")
        .build());
```

## Chat Tokens

`createToken` takes a typed request builder:

```java
Map<String, Object> token = client.chat().createToken(
    Chat.CreateTokenRequest.builder()
        .ttl(60L)
        .channels(Map.of("support", Map.of("read", true, "write", true)))
        .memberId("user-123")
        .build());
```
