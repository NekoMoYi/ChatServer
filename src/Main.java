import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.gson.Gson;

public class Main {
    private static List<ChatClient> clients = new ArrayList<>();
    private static Gson g = new Gson();
    private static ArrayList<User> users = new ArrayList<>();

    public static void login(String data, ChatClient client) {
        Request.LoginRequest loginRequest = g.fromJson(data, Request.LoginRequest.class);
        Response.LoginResponse loginResponse = new Response.LoginResponse(0, "");
        for (User user : users) {
            if (user.username.equals(loginRequest.username) && user.password.equals(loginRequest.password)) {
                client.setClientId(user.id);
                loginResponse = new Response.LoginResponse(user.id, user.token);
            }
        };
        String json = g.toJson(loginResponse);
        client.writer.println("login;" + Base64.getEncoder().encodeToString(json.getBytes()));
    }

    public static void getFriendList(String data, ChatClient client) {
        Request.getFriendListRequest getFriendListRequest = g.fromJson(data, Request.getFriendListRequest.class);
        Response.getFriendListResponse getFriendListResponse = new Response.getFriendListResponse(0, new ArrayList<Entities.Friend>());
        String token = getFriendListRequest.token;
        int userId = tokenToId(token);
        ArrayList<Integer> friends = new ArrayList<Integer>();
        if(userId != 0){
            for (User user : users)
                if (user.id == userId){
                    friends = user.friends;
                    break;
                }
            ArrayList<Entities.Friend> friendsList = new ArrayList<>();
            for (Integer friendId : friends) {
                for (User user : users) {
                    if (user.id == friendId) {
                        friendsList.add(new Entities.Friend(user.username, user.id));
                        break;
                    }
                }
            }
            getFriendListResponse = new Response.getFriendListResponse(1, friendsList);
        }
        String json = g.toJson(getFriendListResponse);
        client.writer.println("getfriends;" + Base64.getEncoder().encodeToString(json.getBytes()));
    }

    public static void getGroupList(String data, ChatClient client) {
        Request.getGroupListRequest getGroupListRequest = g.fromJson(data, Request.getGroupListRequest.class);
        Response.getGroupListResponse getGroupListResponse = new Response.getGroupListResponse(0, new ArrayList<>());
        String token = getGroupListRequest.token;
        int userId = tokenToId(token);
        ArrayList<Integer> groups = new ArrayList<>();
        if(userId != 0){
            for (User user : users)
                if (user.id == userId){
                    groups = user.groups;
                    break;
                }
            ArrayList<Entities.Group> groupList = new ArrayList<>();
            ArrayList<Entities.Friend> groupMembers;
            for (Integer groupId : groups) {
                groupMembers = new ArrayList<>();
                for (User user : users) {
                    if (user.groups.contains(groupId)) {
                        groupMembers.add(new Entities.Friend(user.username, user.id));
                    }
                }
                groupList.add(new Entities.Group(groupId, groupId.toString(), groupMembers));
            }
            getGroupListResponse = new Response.getGroupListResponse(1, groupList);
        }
        String json = g.toJson(getGroupListResponse);
        client.writer.println("getgroups;" + Base64.getEncoder().encodeToString(json.getBytes()));
    }

    public static void sendTextMessage(String data,ChatClient client){
        Request.SendTextMessageRequest sendTextMessageRequest = g.fromJson(data, Request.SendTextMessageRequest.class);
        String token = sendTextMessageRequest.token;
        int userId = tokenToId(token);
        Entities.Friend currentUser = new Entities.Friend("", 0);
        if(userId != 0){
            for (User user : users)
                if (user.id == userId)
                    currentUser = new Entities.Friend(user.username, user.id);
            if(currentUser.id == 0) return;
            Entities.Message msg = new Entities.Message(currentUser, "text", sendTextMessageRequest.message, sendTextMessageRequest.timestamp);
            appendMessageToSession(msg, sendTextMessageRequest.session);
        }
    }

    public static void SendImageMessage(String data, ChatClient client) {
        Request.SendImageMessageRequest sendImageMessageRequest = g.fromJson(data, Request.SendImageMessageRequest.class);
        String token = sendImageMessageRequest.token;
        int userId = tokenToId(token);
        Entities.Friend currentUser = new Entities.Friend("", 0);
        if(userId != 0){
            for (User user : users)
                if (user.id == userId)
                    currentUser = new Entities.Friend(user.username, user.id);
            if(currentUser.id == 0) return;
            Entities.Message msg = new Entities.Message(currentUser, "image", sendImageMessageRequest.imageB64, sendImageMessageRequest.timestamp);
            appendMessageToSession(msg, sendImageMessageRequest.session);
        }
    }

    public static void sendFileMessage(String data, ChatClient client) {
        Request.SendFileMessageRequest sendFileMessageRequest = g.fromJson(data, Request.SendFileMessageRequest.class);
        String token = sendFileMessageRequest.token;
        int userId = tokenToId(token);
        Entities.Friend currentUser = new Entities.Friend("", 0);
        if(userId != 0){
            for (User user : users)
                if (user.id == userId)
                    currentUser = new Entities.Friend(user.username, user.id);
            if(currentUser.id == 0) return;
            Entities.Message msg = new Entities.Message(currentUser, "file", sendFileMessageRequest.filename + '|' +sendFileMessageRequest.fileB64, sendFileMessageRequest.timestamp);
            appendMessageToSession(msg, sendFileMessageRequest.session);
        }
    }

    public static void appendMessageToSession(Entities.Message msg, String session) {
        appendMessageToSession(session, msg);

        ArrayList<Integer> relatedClientIds = new ArrayList<>();
        String sessionId = session;
        if(ChatUtils.isGroupSession(sessionId)){
            sessionId = ChatUtils.b642str(sessionId);
            for (User user : users)
                if (user.groups.contains(Integer.parseInt(sessionId)))
                    relatedClientIds.add(user.id);
        }else{
            sessionId = ChatUtils.b642str(sessionId);
            for (String idStr : sessionId.split("-"))
                relatedClientIds.add(Integer.parseInt(idStr));
        }
        for (ChatClient c : clients) {
            if (c.clientId == 0) continue;
            if (relatedClientIds.contains(c.clientId)) {
                Response.serverMessageResponse serverMessageResponse = new Response.serverMessageResponse(session, msg);
                String json = g.toJson(serverMessageResponse);
                c.writer.println("servermsg;" + Base64.getEncoder().encodeToString(json.getBytes()));
            }
        }
    }

    public static ArrayList<Entities.Message> loadMessageFromSession(String session) {
        ArrayList<Entities.Message> msgList = new ArrayList<>();
        Path sessionFile = Paths.get("content/" + session + ".json");
        if (!Files.exists(sessionFile)) {
            try {
                Files.createFile(sessionFile);
                saveMessageToSession(session, msgList);
            } catch (IOException e) {
                e.printStackTrace();
                return msgList;
            }
        } else {
            try {
                String json = new String(Files.readAllBytes(sessionFile));
                msgList = new ArrayList<>(Arrays.asList(g.fromJson(json, Entities.Message[].class)));
            } catch (IOException e) {
                e.printStackTrace();
                return msgList;
            }
        }
        return msgList;
    }
    public static void saveMessageToSession(String session, ArrayList<Entities.Message> msgList) {
        Path sessionFile = Paths.get("content/" + session + ".json");
        String json = g.toJson(msgList.toArray(new Entities.Message[0]));
        try {
            PrintWriter writer = new PrintWriter(sessionFile.toFile(), StandardCharsets.UTF_8);
            writer.println(json);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void appendMessageToSession(String session, Entities.Message msg) {
        ArrayList<Entities.Message> msgList = loadMessageFromSession(session);
        msgList.add(msg);
        saveMessageToSession(session, msgList);
    }

    public static void getSessionHistory(String data, ChatClient client) {
        Request.getSessionHistoryRequest getSessionHistoryRequest = g.fromJson(data, Request.getSessionHistoryRequest.class);
        Response.getSessionHistoryResponse getSessionHistoryResponse = new Response.getSessionHistoryResponse(0, new ArrayList<>());
        String token = getSessionHistoryRequest.token;
        int userId = tokenToId(token);
        if(userId != 0){
            ArrayList<Entities.Message> msgList = loadMessageFromSession(getSessionHistoryRequest.session);
            getSessionHistoryResponse = new Response.getSessionHistoryResponse(1, msgList);
        }
        String json = g.toJson(getSessionHistoryResponse);
        client.writer.println("getsession;" + Base64.getEncoder().encodeToString(json.getBytes()));
    }

    public static void addFriend(String data, ChatClient client){
        Request.addFriendRequest addFriendRequest = g.fromJson(data, Request.addFriendRequest.class);
        Response.addFriendResponse addFriendResponse = new Response.addFriendResponse(0, "User not found");
        String token = addFriendRequest.token;
        int userId = tokenToId(token);
        if(userId != 0){
            User currentUser = null;
            User targetUser = null;
            for (User user : users) {
                if (user.id == userId)
                    currentUser = user;
                if (user.username.equals(addFriendRequest.username))
                    targetUser = user;
            }
            if(currentUser != null && targetUser != null){
                if(currentUser.friends.contains(targetUser.id))
                    addFriendResponse = new Response.addFriendResponse(0, "Already friends");
                else if(currentUser.id == targetUser.id)
                    addFriendResponse = new Response.addFriendResponse(0, "Cannot add yourself");
                else{
                    currentUser.friends.add(targetUser.id);
                    targetUser.friends.add(currentUser.id);
                    saveUsers();
                    addFriendResponse = new Response.addFriendResponse(1, "Success");
                    for(ChatClient c : clients) {
                        if(c.clientId == targetUser.id){
                            Request.getFriendListRequest getFriendListRequest = new Request.getFriendListRequest(targetUser.token);
                            getFriendList(g.toJson(getFriendListRequest), c);
                            break;
                        }
                    }
                }
            }
        }
        String json = g.toJson(addFriendResponse);
        client.writer.println("addfriend;" + Base64.getEncoder().encodeToString(json.getBytes()));
    }

    public static void handleMessage(String msg, ChatClient client) {
        String op = msg.split(";")[0];
        String data = msg.split(";")[1];
        data = new String(Base64.getDecoder().decode(data));
        switch (op) {
            case "login":
                login(data, client);
                break;
            case "getfriends":
                getFriendList(data, client);
                break;
            case "getgroups":
                getGroupList(data, client);
                break;
            case "txtmsg":
                sendTextMessage(data, client);
                break;
            case "getsession":
                getSessionHistory(data, client);
                break;
            case "addfriend":
                addFriend(data, client);
                break;
            case "imgmsg":
                SendImageMessage(data, client);
                break;
            case "filemsg":
                sendFileMessage(data, client);
                break;
        }
    }

    public static void loadUsers() throws IOException {
        String json = "";
        if (Files.exists(Paths.get("users.json"))){
            json = new String(Files.readAllBytes(Paths.get("users.json")));
            users.clear();
            User[] usersArray = g.fromJson(json, User[].class);
            users.addAll(Arrays.asList(usersArray));
        }
        System.out.println(users.size() + " users in total");
    }

    public static void saveUsers(){
        String json = g.toJson(users.toArray(new User[0]));
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("users.json", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writer.println(json);
        writer.close();
    }
    public static int tokenToId(String token){
        for (User user : users) {
            if (user.token.equals(token)) {
                return user.id;
            }
        };
        return 0;
    }
    public static void main(String[] args) throws IOException {
        loadUsers();
        try {
            ServerSocket serverSocket = new ServerSocket(8189);
            System.out.println("Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("connected：" + clientSocket);

                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                ChatClient client = new ChatClient(writer);
                clients.add(client);

                Thread t = new Thread(() -> {
                    Scanner scanner = null;
                    try {
                        scanner = new Scanner(clientSocket.getInputStream());
                        while (true) {
                            String msg;
                            try {
                                msg = scanner.nextLine();
                            } catch (NoSuchElementException e) {
                                break;
                            }
                            handleMessage(msg, client);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        System.out.println("disconnected: " + clientSocket);
                        clients.remove(client);
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                t.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}