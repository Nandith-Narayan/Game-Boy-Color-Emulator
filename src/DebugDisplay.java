import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class DebugDisplay extends JFrame{
    Color colors[];
    BufferedImage img,prev;
    boolean ready = false;
    public DebugDisplay(){
        colors = new Color[4];
        colors[0] = new Color(255,255,255);
        colors[1] = new Color(85*2,85*2,85*2);
        colors[2] = new Color(85,85,85);
        colors[3] = new Color(0,0,0);
        img = new BufferedImage(256,512, BufferedImage.TYPE_INT_ARGB);
        prev = new BufferedImage(256,512, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().setColor(Color.green);
        img.getGraphics().fillRect(0,0,512,256);
        this.setSize(256,512);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
    }
    @Override
    public void paint(Graphics g){
        if(ready)
            g.drawImage(img,0,0,this);
        else
            g.drawImage(prev,0,0,this);
        repaint();
        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        int x,y;
        ready = false;
        Graphics g = img.getGraphics();
        g.setColor(Color.green);
        g.fillRect(0,0,img.getWidth(),img.getHeight());
        int tileAddressingMode = Memory.read(0x0FF40)&0x00000010;
        int rowSize = 16;

        for(int tile=0;tile<16*16;tile++){
            g.drawImage(getTile(tile,tileAddressingMode),8+((tile%rowSize)*8),(tile/rowSize)*8,8,8 ,this);
        }
        for(int tile=0;tile<16*16;tile++){
            g.drawImage(getTile2(tile,tileAddressingMode),8+((tile%rowSize)*8),256+((tile/rowSize)*8),this);
        }
        prev.getGraphics().drawImage(img,0,0,this);
        ready = true;



    }
    public BufferedImage getTile(int tileNum, int tileAddressingMode){
        int address;
        if(tileAddressingMode == 0){
            address = 0x01000 + (((byte)tileNum)*16);
        }else{
            address = 0x00000 + ((tileNum & 0x0000FF) * 16);
        }

        BufferedImage image = new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        for(int i=0;i<16;i+=2) {
            byte low = Memory.vram[address + i];
            byte high = Memory.vram[address + 1 + i];
            for (int j = 0; j < 8; j++) {
                int val = ((low & 0x00000080) >>> 6) | ((high & 0x00000080) >>> 7);
                low <<= 1;
                high <<= 1;

                g.setColor(colors[val]);
                g.fillRect(j, i/2, 1, 1);

            }

        }

        return image;

    }
    public BufferedImage getTile2(int tileNum, int tileAddressingMode){
        int address;
        if(tileAddressingMode == 0){
            address = 0x01000 + (((byte)tileNum)*16);
        }else{
            address = 0x00000 + ((tileNum & 0x0000FF) * 16);
        }
        //address += 0x01000;
        BufferedImage image = new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        for(int i=0;i<16;i+=2) {

            byte low = Memory.vram[address + i];
            byte high = Memory.vram[address + 1 + i];

            for (int j = 0; j < 8; j++) {
                int val = ((low & 0x00000080) >>> 6) | ((high & 0x00000080) >>> 7);
                low <<= 1;
                high <<= 1;

                g.setColor(colors[val]);
                g.fillRect(j, i/2, 1, 1);

            }

        }


        return image;

    }
}
