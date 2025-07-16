package it.elebor;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by glauco on 16/12/2024.
 */
public class KernelMonitor extends KernelEthernet{

    SocketChannel socket;

    public KernelMonitor(KPT parent, String ipAddr) {
        super(parent, ipAddr);
    }

    public void run (boolean cmd, int []dataAddr, int val) {
        run(cmd,dataAddr,val,false,0,0);
    }

    public void run (boolean cmd, int []dataAddr, int val, boolean repeat,
                     int t, int d) {
        int counter=0;
        do {
            System.out.println("--- " + counter + " ---" );
            for (int iter = 0; iter < dataAddr.length; iter++){
                try {
                    if (cmd) //scrive
                        writeIntVal(ipAddr, dataAddr[iter], val);
                    else //legge
                        readIntVal(ipAddr, dataAddr[iter]);
                } catch (Exception ex) {
                    System.out.println("ipAddr:" + ipAddr + " dataAddr:" + dataAddr + " Val:" + val);
                    ex.printStackTrace();
                }
            }
            if (t > 0)
                counter++;
            if (counter >= t)
                repeat = false; //termina
            if (repeat) {
                try {
                    Thread.currentThread().sleep(d * 100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (repeat);

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * read a word from the specifiead ip address at the memory address
     * @param ipAddress PLC IP address
     * @param address PLC memory address
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    public  synchronized int readIntVal(String ipAddress,int address) throws MalformedURLException, IOException {

        String esadecimale=Integer.toHexString(address);

        String ciccio = "00d"+ (("0000" + esadecimale).substring(esadecimale.length()))+"01";
        String res = esegueRichiesta(ciccio,ipAddress);
        int val=Integer.parseInt(res, 16);
        if (address!=1010)
            System.out.println("readIntVal -> PLC:"+ipAddress+":DATA."+address+" ricevuto: "+val );

        //parent.debug("readIntVal: richiesta: fatta ricevuto: "+val);
        return val;
    }


    /**
     * write into a word from the specidiead ip address at the memory address
     * @param ipAddress PLC IP address
     * @param address PLC memory address
     * @param valore the value to be written
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    public synchronized boolean writeIntVal(String ipAddress,int address, int valore) throws MalformedURLException, IOException {

        String ciccio = "00D"+intTo4HexChar(address)+intTo4HexChar(valore)+EOT;
        String res = esegueRichiesta(ciccio,ipAddress);
        System.out.println("writeIntVal -> PLC: "+ipAddress+"-> DATA."+address+" = "+valore +". Ricevuto: " + res);

        return true;
    }



    /**
     * send the data packet to the destination
     * @param payload data to send
     * @param ipAddress IP address of the PLC
     * @return
     */
    private synchronized String esegueRichiesta(String payload,String ipAddress) {
        String pacchetto = calcolaCheck(payload);
        parent.debug("esegueRichiesta: invia pacchetto="+pacchetto);

        ByteBuffer bufIn;
        bufIn = ByteBuffer.allocate(48);
        try {
            // crea il socket
            if ((socket == null)|| (!socket.isOpen()) || (!socket.isConnected())) {
                parent.debug("esegueRichiesta: create the socket [" + ipAddress + "] :" + PORT);
                socket = SocketChannel.open();
                socket.connect(new InetSocketAddress(ipAddress, PORT));
            }

            // invia il pacchetto
            parent.debug("esegueRichiesta: invia il pacchetto: " + pacchetto);
            ByteBuffer buf = ByteBuffer.allocate(pacchetto.getBytes().length);
            buf.clear();
            buf.put(pacchetto.getBytes());
            buf.flip(); // "riavvolge" il buffer

            while(buf.hasRemaining()) {
                socket.write(buf);
            }

            // Read data from the server until we finish reading the document
            try {
                parent.debug("esegueRichiesta: attende risposta.");
                //int bytesRead = socket[plc].read(bufIn);
                socket.read(bufIn);
            } catch (EOFException eofEx) {
                eofEx.printStackTrace();
                socket.close();
                socket=null;
            } catch (Exception e){
                e.printStackTrace();
                socket.close();
                socket=null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        bufIn.flip();
        String ret = new String(bufIn.array()).trim();//toglie spazi,STX e ETX
        return ret.substring(0, ret.length() - 2);//toglie CRC
    }
}
