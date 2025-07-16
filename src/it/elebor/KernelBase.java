package it.elebor;

/**
 * Created by glauco on 16/12/2024.
 */
abstract public class KernelBase {

    static final char STX = 0x02;
    static final char ACK = 0x06;
    static final char NACK= 0x16;
    static final char ETX = 0x03;
    static final char EOT = 0x04;

    /**
     * implements a delay in execution
     * @param l
     */
    public void pausa(long l){
        try {
            Thread.currentThread().sleep(l); //'
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * calculate the il checksum of the payload ad append the transmission control characters
     * @param payload the dat to be sent
     * @return the formatted string: STX + payload + chk + ETX
     */
    protected String calcolaCheck(String payload) {
        char c;
        int checksum = 0;

        for (int i = 0; i < payload.length(); i++) {
            c = payload.charAt(i);
            checksum += c;
        }
        checksum = checksum % 256;
        String cv = (Integer.toHexString(checksum)).toUpperCase();
        if (cv.length() < 2)
            return STX + payload + "0" + cv + ETX;
        return STX + payload + cv + ETX;
    }
    /**
     * execute the padding to 4 characters of an integer in hexadecimal format
     * @param i the value to convert
     * @return four characters: cccc
     */
    protected String intTo4HexChar(int i) {
        String s = Integer.toHexString(i).toUpperCase();
        switch (s.length()) {
            case 1:
                return "000" + s;
            case 2:
                return "00" + s;
            case 3:
                return "0" + s;
            default:
                return s;
        }
    }

    /**
     * execute the padding to 8 characters of an integer in hexadecimal format
     * @param i the value to convert
     * @return four characters: cccccccc
     */
    protected String intTo8HexChar(int i) {
        String s = Integer.toHexString(i).toUpperCase();
        switch (s.length()) {
            case 1:
                return "0000000" + s;
            case 2:
                return "000000" + s;
            case 3:
                return "00000" + s;
            case 4:
                return "0000" + s;
            case 5:
                return "000" + s;
            case 6:
                return "00" + s;
            case 7:
                return "0" + s;
            default:
                return s;
        }
    }

}
