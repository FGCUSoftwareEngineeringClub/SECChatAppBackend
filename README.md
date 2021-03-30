# SEC Backend Chat Server

Built as part of a series of sessions for teaching iOS and Android development.

This project serves as a backend server for a basic chat application with the following features:

- Login/Registration
- Sending Messages
- Group Chats
- Live Updates

## Endpoints:

### User:

| Method | Path            | Description        | Authenticated? | Extra Params? | Returns          | 
|--------|-----------------|--------------------|----------------|---------------|------------------|
| GET    | /user/me        | Get current user   | Yes            | N/A           | `User`           |
| GET    | /user/:username | Get user with name | Yes            | N/A           | `User OR Result` |
| GET    | /user/users     | Get all users      | Yes            | N/A           | `List<User>`     |
| POST   | /user/register  | Create new user    | No             | Yes           | `User OR Result` |

### Chat:

| Method | Path                                     | Description                        | Authenticated? | Extra Params? | Returns                  | 
|--------|------------------------------------------|------------------------------------|----------------|---------------|--------------------------|
| GET    | /chat/recent                             | Get user conversations             | Yes            | N/A           | `List<Conversation>`     |
| POST   | /chat/thread                             | Create new conversation            | Yes            | Yes           | `Conversation OR Result` | 
| GET    | /chat/thread/:threadId                   | Get conversation with id           | Yes            | N/A           | `Conversation OR Result` | 
| GET    | /chat/thread/:threadId/messages          | Get messages for conversation      | Yes            | N/A           | `List<Message>`          |
| POST   | /chat/thread/:threadId/message           | Send a message to conversation     | Yes            | Yes           | `Nothing`                |
| GET    | /chat/thread/:threadId/members           | Get all users in conversation      | Yes            | N/A           | `List<User>`             |
| POST   | /chat/thread/:threadId/members/:username | Add user to conversation           | Yes            | N/A           | `Result`                 |
| DELETE | /chat/thread/:threadId/members/:username | Remove user from conversation      | Yes            | N/A           | `Result`                 |
| WS/GET | /chat/recent                             | Get recent conversation updates    | Yes            | N/A           | `List<Conversation>`     |
| WS/GET | /chat/thread/:threadId/messages          | Get live messages for conversation | Yes            | N/A           | `List<Conversation>`     |


## Database Schema:

### User:

Describes a single user with their username and display name.

```json
{
  "username": "timcook",
  "displayName": "Tim Cook"
}
```

### Result:

A response containing the result code of a request and a message. This is only returned for status updates such as if a 
request fails or if the request has no meaningful information to be returned. Neither the `message` nor `code` will ever
be null if a result is received.

```json
{
  "message": "User not found",
  "code": 404
}
```

### Message:

A single message from a user. The `time` field is in milliseconds since epoch (Jan 1, 1970).

```json
{
  "conversationId": 1,
  "id": 4,
  "author": {
    "username": "jonyive",
    "displayName": "Jony Ive"
  },
  "text": "Completely agree. We'll talk later.",
  "time": 1616464214855
}
```

### Conversation:

A conversation between several users. The `owner` field contains information about the owner/creator of the conversation.
The `message` field may be `null` if there are no messages in the conversation yet. 

```json
{
  "id": 1,
  "name": "Underwater Basketweaving Discussions",
  "owner": {
    "username": "timcook",
    "displayName": "Tim Cook"
  },
  "lastMessage": {
    "conversationId": 1,
    "id": 4,
    "author": {
      "username": "jonyive",
      "displayName": "Jony Ive"
    },
    "text": "Completely agree. We'll talk later.",
    "time": 1616464214855
  }
}
```

## Route Details:

### POST /user/register

Creates a new user.

**Required Body:**

```json
{
  "username": "timapple",
  "displayName": "Tim Cook",
  "password": "secretpassword"
}
```

**Example Response:**

```json
{
  "username": "timapple",
  "displayName": "Tim Cook"
}
```

**Example Error Response:**
```json
{
  "message": "A user with that name already exists.",
  "code": 400
}
```

### POST /chat/thread

Creates a new conversation.

**Query Parameters:**

| Name     | Required | Type                      | Description                                        | 
|----------|----------|---------------------------|----------------------------------------------------|
| name     | No       | String                    | The name of the conversation                       |
| partners | Yes      | Space-separated usernames | Users that should be added to the new conversation |   

Example Request:

- `name`: My new conversation -> becomes "name=My%20new%20conversation"
- `partners`: ["jairo", "timcook"] -> becomes "partners=jairo%20timcook"

**Final URL:**

`/chat/thread?name=My%20new%20conversation&partners=jairo%20timcook`

*Note that space characters are replaced with '%20' in URLs. Check your target platform to see if this is automatically done.*

**Example Request and Response:**

Request: `/chat/thread?name=My%20new%20conversation&partners=jairo%20timcook`

Response:
```json
{
  "id": 3,
  "name": "My new conversation",
  "owner": {
    "username": "jonyive",
    "displayName": "Jony Ive"
  },
  "lastMessage": {
    "conversationId": 3,
    "id": 9,
    "author": {
      "username": "jonyive",
      "displayName": "Jony Ive"
    },
    "text": "@jonyive started a chat.",
    "time": 1616465411215
  }
}
```


### POST /chat/thread/:threadId/message

Send a message to a conversation

**Query Parameters:**

| Name     | Required | Type                      | Description             | 
|----------|----------|---------------------------|-------------------------|
| text     | Yes      | String                    | The body of the message |

Example Request:

- `text`: "Hey buddy!" -> becomes "text=Hey%20buddy!"

**Final URL:**

`/chat/thread/3/message?text=Hey%20buddy!`

**Example Request and Response:**

Request: `/chat/thread/3/message?text=Hey%20buddy!`

Response: No body will be returned and a 200 response will be received.

## Authentication

Most of the endpoints in the API are protected by a Basic authentication scheme. When sending a request to a protected
route, an `Authorization` header must be provided with a value of `Basic $base64EncodedCredentials`

**Example:**

For a username `timapple` and a password `password`, the two fields should be joined by a colon then encoded as a base64
string.

- Join fields with colon: `timapple:password`
- Convert to base64 (see your platform docs on how to do so)

**Java:**

```java
import java.util.Base64;

class MyClass {
    public static void main(String[] args) {
        String combined = "timapple:password";
        ByteArray bytes = Base64.getEncoder().encode(combined.getBytes());
        
        String base64Encoded = new String(bytes); // encoded parameters
        
        String value = "Basic " + base64Encoded; // This would be the value of the "Authorization" header
    }
}
```

### WebSockets:

Authentication when using websocket routes does not rely on the Authorization header for authentication. Instead, upon
opening a websocket, the client will not receive any data until it has first sent its authorization parameters. During
websocket authentication, only the encoded username and password should be sent.

Example:

- Username = `timapple`
- Password = `password`
- Combined = `timeapple:password`
- Encoded  = `dGltZWFwcGxlOnBhc3N3b3Jk`

When opening a websocket, the client should send `"dGltZWFwcGxlOnBhc3N3b3Jk"` in order to start receiving data.

