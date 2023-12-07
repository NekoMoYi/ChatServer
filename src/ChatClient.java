import java.util.Scanner;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    public int clientId = 0;
    public PrintWriter writer;

    public ChatClient(PrintWriter writer) {
        this.writer = writer;
    }
    public ChatClient(){
        this.writer = null;
    }
    public void setClientId(int clientId) {
        this.clientId = clientId;
    }
}
