import java.util.ArrayList;

public class User {
    int id;
    String username;
    String password;
    String token;
    ArrayList<Integer> friends = new ArrayList<Integer>();
    ArrayList<Integer> groups = new ArrayList<Integer>();

    public User(int userId, String username, String password, String token, ArrayList<Integer> friends, ArrayList<Integer> groups) {
        this.id = userId;
        this.username = username;
        this.password = password;
        this.token = token;
        this.friends = friends;
        this.groups = groups;
    }
}
