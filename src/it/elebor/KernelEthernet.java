package it.elebor;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by glauco on 13/12/2024.
 */
public class KernelEthernet extends KernelBase {

    Terminal Terminale= new Terminal();

    public static final int SYSTEM_START_100  = 0x8002800; //' Indirizzo iniziale del Sistema Operativo dell' ARM SERIE 100
    public static final int SYSTEM_START_200  = 0x8004000; //' Indirizzo iniziale del Sistema Operativo dell' ARM SERIE 200

    SocketChannel ClientSocket;
    KPT parent;
    String ipAddr;

    static final int PORT=80;

    boolean FlgRemote=true;
    boolean FlgOK=true;
    boolean FlgClose=false;
    boolean FlgAnnulla = false;


    String Messaggio;
    String Titolo;

    String Risposta;

    public KernelEthernet(KPT parent,String ipAddr) {
        this.parent = parent;
        this.ipAddr=ipAddr;
    }





    /**
     * crea un socket, eventualmente chiude il precedente se esiste già
     * @return
     */
    public boolean creaConnessione() {


        if (ClientSocket!=null) {
            parent.debug("creaConnessione: chiude il socket");
            try {

                if (ClientSocket.isOpen())
                    ClientSocket.close();

                if (ClientSocket.isConnected())
                    ClientSocket.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                parent.debug("creaConnessione: impossibile chiudere il socket");
                return false;
            }
        }

        parent.debug("creaConnessione: attende 2 secondi prima di ricercare il PLC si e' resettato ed e' ripartito");
        pausa(2000);

        parent.debug("creaConnessione: crea il socket [" + ipAddr + "] port:" + PORT);

        int i=0;

        do {
            try {
                parent.debug ("creaConnessione: attesa riconnessione tentativo: "+ i);
                ClientSocket = SocketChannel.open();
                ClientSocket.connect(new InetSocketAddress(ipAddr, PORT));
                return true;
            }catch (Exception ex) {
                i++;
            }
        } while  (i<3);
        parent.debug("creaConnessione: annullato tentativo di connessione.");
        return false;
    }

    /**
     *
     * @param CheckRisp
     * @param FlgMessage
     * @param TimeOutAttesa
     * @param Messagge
     * @return
     */
    public String AttesaStringaRemoto(String CheckRisp , boolean FlgMessage , int TimeOutAttesa , String Messagge ) {

        long CntTimeOutTX = System.currentTimeMillis();// ' Conta l' attesa della risposta
        ByteBuffer bufIn;
        bufIn = ByteBuffer.allocate(48);
        try {
            do{
                int RdByte; // "Messagge x sec."
                if (!Messagge.equals("")) {
                    long ora=System.currentTimeMillis();

                    if(((ora - CntTimeOutTX) / 1000) == 0){
                        parent.debug( "AttesaStringaRemoto");
                    } else{
                        parent.debug( "AttesaStringaRemoto" + (ora / 1000)+ " sec.");
                    }
                }

                try {
                    //debug("attende risposta.");
                    //int bytesRead = socket[plc].read(bufIn);
                    ClientSocket.read(bufIn);
                } catch (EOFException eofEx) {
                    eofEx.printStackTrace();
                    ClientSocket.close();
                    ClientSocket=null;
                } catch (Exception e){
                    e.printStackTrace();
                    ClientSocket.close();
                    ClientSocket=null;
                }
                bufIn.flip();

                //Risposta = new String(bufIn.array()).trim();//toglie spazi,STX e ETX
                Risposta = new String(bufIn.array());//NON toglie spazi,STX e ETX
                //parent.debug("AttesaStringaRemoto ricevuto: "+Risposta+" atteso: "+CheckRisp);

                //If FlgAnnulla = True Then Throw New ApplicationException(ResourcesLang(316))
            } while (((System.currentTimeMillis() - CntTimeOutTX) < (TimeOutAttesa * 1000))
                    && (!Risposta.contains(CheckRisp)));
            //aspetto la risposta "CheckRisp"

            parent.debug("AttesaStringaRemoto termina.");

        }catch (Exception ex) {
            ex.printStackTrace();
            if (FlgMessage == true) {
                Risposta = ex.getMessage();
            } // Non e' arrivata la risposta che mi aspettavo
        }


        return Risposta; // Riposta arrivata correttamente
    }


    /**
     *
     * @param Message data to send
     * @param NumErr    erro code to print
     * @param FlgWaitAnswer
     * @param TimeToWait if <0 doesn't wait for answer
     * @return
     */
    public boolean WriteRemoteCommand(String Message, int NumErr, boolean FlgWaitAnswer , int TimeToWait) {
        ByteBuffer buf = ByteBuffer.allocate(Message.getBytes().length);
        buf.clear();
        buf.put(Message.getBytes());
        buf.flip(); // "riavvolge" il buffer
        try {
            while (buf.hasRemaining()) {
                ClientSocket.write(buf);
            }
            parent.debug("WriteRemoteCommand: comando inviato: "+Message);
            // Quando arriva l' ETX la risposta e ' terminata
            if (TimeToWait<0){
                parent.debug("WriteRemoteCommand: Comando senza risposta attende 1,5 sec.");
                pausa(1500);
                return true;
            }
            Risposta = AttesaStringaRemoto(""+ETX, true, TimeToWait, Message);
            if (FlgWaitAnswer == true) {
                // Attendo la risposta! Se non e' quella prevista segnalo l 'errore ed esco
                if (!Risposta.contains(""+ACK)) {
                    if (FlgAnnulla == true) {
                        Termina(); //' Termina la comunicazione seriale
                        parent.debug("non ricevuti ACK");
                        FlgOK = false;
                        FlgClose = true;
                    } else {
                        Transmission_Error("WriteRemoteCommand: errore in scrittura:" + NumErr); // segnalo l' errore
                    }
                    return false; // ... ed esco dalla routine!
                }
                pausa(500);
            }
        } catch (Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }


    /**
     *
     * @param Message   dati da inviare
     * @param TimeWaitRestart
     * @param FlgSendStream
     * @return
     */
    public boolean  SendAndWaitRestart(String Message , long TimeWaitRestart , boolean FlgSendStream ) {
        parent.debug("SendAndWaitRestart invia: " + Message);
        try {
            if (FlgSendStream == true) {
                ByteBuffer buf = ByteBuffer.allocate(Message.getBytes().length);
                buf.clear();
                buf.put(Message.getBytes());
                buf.flip(); // "riavvolge" il buffer

                while (buf.hasRemaining()) {
                    ClientSocket.write(buf);
                }
                parent.debug("SendAndWaitRestart: messaggio inviato ");
                pausa(TimeWaitRestart);
            }

        } catch (Exception ex){
            ex.printStackTrace();
            return false;
        }
        for (int i=0; i<3;i++){
            try {
                if (creaConnessione()){
                    parent.debug("Riconnessione avvenuta in tntativi: " + i);
                    return true;
                }
                pausa(200);
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }
        parent.debug("Riconnessione NON avvenuta.");
        return false;

    }

    public void Transmission_Error(String Message_Error) {
        if (!Message_Error.equals("")) {
            parent.debug("Transmission_Error: "+Message_Error);
            Ker_Command("RESET", false, "" + ACK); // Invia un comando di "RESET" x riavviare il terminale
            parent.debug("Inviato comando di reset");
            pausa(500l); //Attende x msec.
            Termina();  //Procedura che termina la comunicazione seriale
            if (!Message_Error.equals("")){
                Messaggio=Message_Error;
                Titolo="errore generico";
            }else {
                Messaggio = "";
                Titolo = "";
            }
            FlgOK=false;
            FlgClose=true;
        }
    }

    public String Ker_Command(String StrCommand , boolean FlgOnlyCommand , String CheckRisp) {
        String Risposta="" ;
        try {
            String StrSend;
            if (FlgOnlyCommand) { // Flag che serve x decidere se e' necessario...
                StrSend = StrCommand;// ' inviare il comando "StrCommand" così com' è...
            }else{ // ' ...oppure aggiungerci "STX" all' inizio e Check Sum +"ETX" alla fine !
                StrSend =  calcolaCheck("00" + StrCommand) ;
            }
            if (FlgRemote ) {
                ByteBuffer buf = ByteBuffer.allocate(StrSend.getBytes().length);
                buf.clear();
                buf.put(StrSend.getBytes());
                buf.flip(); // "riavvolge" il buffer

                while (buf.hasRemaining()) {
                    ClientSocket.write(buf);
                }

                pausa(100);
                // Non arriva nessuna risposta perchè si resetta il PLC e si chiude il socket
            }
        }catch ( Exception ex) {
            Risposta = ex.getMessage(); // Non e' arrivata la risposta che mi aspettavo
        }
        return Risposta; // ' Riposta arrivata correttamente
    }


    /**
     * cose the socket
     */
    public void Termina(){


            if (ClientSocket != null) {
                try {
                    ClientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ClientSocket = null;
            }

    }


    class Terminal{
        public boolean FlgInvPagPlc=true;
        public String Microprocessore="ARM,SERIES_200";
    }
}
