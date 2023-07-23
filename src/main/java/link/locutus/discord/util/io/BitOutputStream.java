package link.locutus.discord.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class BitOutputStream {

    private OutputStream out;
    private boolean[] buffer = new boolean[8];
    private int count = 0;

    public BitOutputStream(OutputStream out) {
        this.out = out;
    }

    public void clear() {
        Arrays.fill(this.buffer, false);
        this.count = 0;
    }

    public void write(boolean x) throws IOException {
        this.count++;
        this.buffer[8-this.count] = x;
        if (this.count == 8){
            int num = 0;
            for (int index = 0; index < 8; index++){
                num = 2*num + (this.buffer[index] ? 1 : 0);
            }

            this.out.write(num - 128);

            this.count = 0;
        }
    }

    public void close() throws IOException {
        int num = 0;
        for (int index = 0; index < 8; index++){
            num = 2*num + (this.buffer[index] ? 1 : 0);
        }

        this.out.write(num - 128);

        this.out.close();
    }

}