package com.company;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class ImageSender {


    public static int HEADER_SIZE = 8;
    public static int MAX_PACKETS = 255;
    public static int SESSION_START = 128;
    public static int SESSION_END = 64;
    public static int DATAGRAM_MAX_SIZE = 65507 - HEADER_SIZE;
    public static int MAX_SESSION_NUMBER = 255;

    /*
     * Dimensiunea maxim? a pachetului IP este de 65535 minus 20 octe?i pentru IP, minus 8
     *  octe?i pentru antetul UDP, datagrama devine 65507
     */
    public static String OUTPUT_FORMAT = "jpg";

    public static int COLOUR_OUTPUT = BufferedImage.TYPE_INT_RGB;


    public static double SCALING = 0.5; //coeficient de reducere a dimensiunii imaginii
    public static int SLEEP_MILLIS = 2000; //timpul de a?teptare
    public static String IP_ADDRESS =  "225.4.3.1";
    public static int PORT = 4444;


    /* Codul de zon? captur? de ecran*/
    public static BufferedImage getScreenshot() throws AWTException,
            ImagingOpException, IOException {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        Rectangle screenRect = new Rectangle(screenSize);

        Robot robot = new Robot();
        BufferedImage image = robot.createScreenCapture(screenRect);

        return image;
    }





    public static byte[] imageyibyteyap(BufferedImage image, String format) throws IOException { //convertim imaginea în matrice de octe?i
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); //Crearea matricei
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }


    public static BufferedImage olcek(BufferedImage source, int w, int h) {
        Image image = source
                .getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING);
        BufferedImage result = new BufferedImage(w, h, COLOUR_OUTPUT);
        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }


    public static BufferedImage kucult(BufferedImage source, double factor) {
        int w = (int) (source.getWidth() * factor);
        int h = (int) (source.getHeight() * factor);
        return olcek(source, w, h);
    }




    private boolean gonder(byte[] imageData, String multiAddress,
                           int port) {
        InetAddress ia;

        boolean ret = false;
        int ttl = 2; //timpul de viata pentru fiecare rutare


        try {
            ia = InetAddress.getByName(multiAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return ret;
        }

        MulticastSocket ms = null;

        try {
            ms = new MulticastSocket();
            ms.joinGroup(ia);
            ms.setTimeToLive(ttl);
            DatagramPacket dp = new DatagramPacket(imageData, imageData.length,
                    ia, port);
            ms.send(dp);
            ret = true;
        } catch (IOException e) {
            e.printStackTrace();
            ret = false;
        } finally {
            if (ms != null) {
                ms.close();
            }
        }

        return ret;
    }


    public static void main(String[] args) {  //start main
        ImageSender sender = new ImageSender(); //Creeaz? un obiect
        int sessionNumber = 0; // nr de sesiuni, mereu incepem cu 0



        // Frame
        JFrame frame = new JFrame("Transmi??tor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel label = new JLabel();
        frame.getContentPane().add(label);
        frame.setVisible(true);



        label.setText("Ob?inerea de capturi de ecran");


        frame.pack();

        try {
            /* trimiterea continua*/
            while (true) {
                BufferedImage image;

                /* Dac? exist? o imagine, nu exist? o captur? de ecran aleatorie */

                image = getScreenshot();



                /* scalarea imaginii */
                image = kucult(image, SCALING); //mic?oram imaginea
                byte[] imageByteArray = imageyibyteyap(image, OUTPUT_FORMAT); //o punem în matricea de octe?i ca jpg.
                int packets = (int) Math.ceil(imageByteArray.length / (float)DATAGRAM_MAX_SIZE); //Calcul?m dimensiunea matricei noastre de octe?i. Câte datagrame ies din matricea de octe?i?

                /* If image has more than MAX_PACKETS slices -> error */
                if(packets > MAX_PACKETS) { //dac? num?rul de pachete este mai mare de 255
                    System.out.println("Imaginea este prea mare");
                    continue;
                }


                for(int i = 0; i <= packets; i++) {
                    int flags = 0;
                    flags = i == 0 ? flags | SESSION_START: flags; // Dac? abia începem, începem flagul din primul pachet 128
                    flags = (i + 1) * DATAGRAM_MAX_SIZE > imageByteArray.length ? flags | SESSION_END : flags; //dac? am ajuns la ultimul pachet flag+64

                    int size = (flags & SESSION_END) != SESSION_END ? DATAGRAM_MAX_SIZE : imageByteArray.length - i * DATAGRAM_MAX_SIZE; //anunta sfarsit de sesiune

                    /* în interiorul antetului  */
                    byte[] data = new byte[HEADER_SIZE + size]; //Antetul nostru de 8 bi?i + dimensiunea creeaz? pachetul nostru de datagrame.
                    data[0] = (byte)flags; // Sesiunea con?ine informa?ii despre flagul cu început sau sfâr?it
                    data[1] = (byte)sessionNumber; // Con?ine informa?ii despre câte sesiuni de pachete sunt
                    data[2] = (byte)packets; // Con?ine informa?ii totale despre pachet
                    data[3] = (byte)(DATAGRAM_MAX_SIZE >> 8); //dimensiunea datagramei nu iese complet
                    data[4] = (byte)DATAGRAM_MAX_SIZE; // 1 Dimensiunea total? a datagramei
                    data[5] = (byte)i; //continuam
                    data[6] = (byte)(size >> 8); //
                    data[7] = (byte)size;

                    /* copiem imaginea */
                    System.arraycopy(imageByteArray, i * DATAGRAM_MAX_SIZE, data, HEADER_SIZE, size); //Am pus partea marcat? a informa?iilor despre imagine în matricea noastr? de date
                    /* Trimitem pachetul nostru*/
                    sender.gonder(data, IP_ADDRESS, PORT);

                    /* Leave loop if last slice has been sent */
                    if((flags & SESSION_END) == SESSION_END) break; //Dac? este sfâr?itul, trimiterea pachetului este complet?.
                }
                /* Sleep */
                Thread.sleep(SLEEP_MILLIS);

                /* nr sesiunii creste*/
                sessionNumber = sessionNumber < MAX_SESSION_NUMBER ? ++sessionNumber : 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    } //end of main

}