public class Pixel {
    int color;
    int palette;
    boolean spritePriority;
    boolean backgroundPriority;

    public Pixel(int color, int palette){
        this.color = color;
        this.palette = palette;
    }
    public Pixel(int color, int palette, boolean backgroundPriority){
        this.color = color;
        this.palette = palette;
        this.backgroundPriority = backgroundPriority;
    }

}
