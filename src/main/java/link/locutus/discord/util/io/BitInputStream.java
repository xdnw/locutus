package link.locutus.discord.util.io;
import java.io.IOException;
import java.io.InputStream;

public class BitInputStream {

    private InputStream in;
    private int num = 0;
    private int count = 8;

    public BitInputStream(InputStream in) {
        this.in = in;
    }

    public void clear() {
        this.num = 0;
        this.count = 8;
    }

    public boolean read() throws IOException {
        if (this.count == 8){
            this.num = this.in.read() + 128;
            this.count = 0;
        }

        boolean x = (num%2 == 1);
        num /= 2;
        this.count++;

        return x;
    }

    public void close() throws IOException {
        this.in.close();
    }
}