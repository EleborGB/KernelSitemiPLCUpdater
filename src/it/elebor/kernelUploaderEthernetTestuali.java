package it.elebor;

import java.util.ArrayList;

/**
 * Created by glauco on 13/12/2024.
 */
public class kernelUploaderEthernetTestuali extends KernelEthernet {

    boolean FlgAnnulla = false;
    boolean FlgTxPages = true;
    boolean FlgTxPLC   = true;
    boolean FlgLoadAppli = true;

    int FlgTx = parent.PLC;

    ArrayList<String> linee;

    public kernelUploaderEthernetTestuali(KPT parent, ArrayList linee, String ipAddress) {
        super(parent, ipAddress);
        this.linee = linee;
        ipAddr = ipAddress;
    }

    public boolean LoadFileSysARM() {
        String StrToSend;
        int pMaxAddr = linee.size(); //                                   ' e azzero l'indirizzo finale!

        String FileLine;
        boolean FlgSendStr=false;
        boolean FlgIgnora=false;
        int Addr;
        for (int i = 0; i < pMaxAddr; i++) {

            pausa(5); //prima di inviare attende 5 millisecondi

            if (FlgAnnulla)
                return true;
            FileLine=linee.get(i);

            FlgIgnora=false;
            String ck = FileLine.substring(1, 2);

            if (ck.equals("1")) {
                Addr = Integer.parseInt(FileLine.substring(4, 8), 16); // Indirizzo a 4 char
                if (!FlgTxPages)
                    FlgIgnora = true;
            }
            else if (ck.equals("2")){
                Addr = Integer.parseInt(FileLine.substring(4, 10), 16); //' Indirizzo a 6 char
                if (!FlgTxPages)
                    FlgIgnora = true;
            }
            else if (ck.equals("3")){
                Addr = Integer.parseInt(FileLine.substring(4, 12), 16); //' Indirizzo a 8 char
                if (!FlgTxPLC)
                    FlgIgnora = true;
            }
            else if ((ck.equals("7")) || (ck.equals("8")) || (ck.equals("9"))) { // Fine del File
                Addr =SYSTEM_START_200;
                if(Terminale.Microprocessore.toUpperCase().contains("ARM,SERIES_100"))
                    Addr=SYSTEM_START_100;
                FlgIgnora = true;
            }
            else {
                Addr = 0;
                FlgIgnora = true;
            }
            FlgSendStr=false;
            StrToSend="";
            if (FlgTx == parent.HTML){
                if (!FlgIgnora) {
                    StrToSend = "00F" + intTo8HexChar(Addr) + FileLine.substring(12, FileLine.length() - 2) + EOT;
                    FlgSendStr = true;
                }
            } else {
                if (!FlgIgnora ) {
                    StrToSend = "00F" +  intTo8HexChar(Addr) + FileLine.substring(12, FileLine.length() - 2) + EOT;
                    FlgSendStr = true;
                }
            }
            if (FlgSendStr) {

                String sTmp =  calcolaCheck(StrToSend) ; //' STX + 00FC + CheckSum + ETX

                if (!WriteRemoteCommand(sTmp, 0, false, 3000)) {
                    parent.debug(" Errore Time-Out Seriale! Carattere ACK non ricevuto!");
                    FlgOK = false;
                    FlgClose = true;
                    return false;
                }
            }
        }

        pausa(100); //a fine invio attende 1 decimo

        return true;
    }


    public int upload(){
        boolean FlgPageCan = false, FlgAppliCan = false;    // inizializzo i flags
       if (! creaConnessione()){
            return -101;
        }
        /*
        If FlgTx = TX.APPLY Then
                NomeFileSys = NomeFileApply
        ElseIf FlgTx = TX.HTML Then
                NomeFileSys = NomeFilePagesHTML
        End If
        */
        System.out.println("Waiting for permission to upload...");
        String sTmp = "00MENU";
        sTmp = calcolaCheck(sTmp); // STX + 00FERA + CheckSum + ETX
        if (SendAndWaitRestart(sTmp, 5000, true) == false) {
            Transmission_Error("Sincronizzazione non riuscita!"); //" segnalo l' errore..."Sincronizzazione non riuscita!"
            return -103; // ... ed esco dalla routine!
        }
        //****** Comando "menu" x controllare che sia all'interno del menu ****

        for (int i = 1; i < 11; i++) {
            parent.debug("Attendere prego.... " + i);
            if (FlgAnnulla == true) {
                parent.debug("annullato in menu");
                return -104;
            }
            if (i >= 10) { // Se dopo x tentativi non ho ricevuto l' "ok"...
                Transmission_Error("Il microprocessore non mi ha risposto correttamente!");
                return -105;
            } // Invio il comando "menu" e attendo la risposta "ok"

            sTmp = calcolaCheck("00menu"); // STX + 00FERA + CheckSum + ETX
            parent.debug("Invia comando 00menu per verificare la connessione");
            if (WriteRemoteCommand(sTmp, 0, false, 5000) == false) {
                parent.debug("Il microprocessore non mi ha risposto correttamente!");
                return -106;
            } // ... segnalo l' errore...

            if (Risposta.contains("ok")) {
                parent.debug("Riposta= ok interrompe il ciclo for");
                break;
            }

        }


        //***** Clear Serial Flash Command *****************************************************

        sTmp = "00FERA" + EOT;
        sTmp =  calcolaCheck(sTmp) ; // ' STX + 00FERA + CheckSum + ETX
        System.out.println("Clear Serial Flash Command");
        if (!WriteRemoteCommand(sTmp, 415, true, 20000)) {
            //Reader.Close() '... chiudo il file ...
            parent.debug(" Flash Error! Programmazione Interrotta!");
            return -107;
        }
        //***** Unprotect Serial Flash Command *************************************************
        System.out.println("Unprotect Serial Flash Command");
        sTmp = "00FUNP" + EOT;
        sTmp =  calcolaCheck(sTmp);// ' STX + 00FUNP + CheckSum + ETX
        if (!WriteRemoteCommand(sTmp, 317, true, 10000)) {
            parent.debug("Unprotect Serial Flash Command\n" + " PLC non Risponde!");
            return -108;
        }
        FlgPageCan = false;
        FlgAppliCan = true;         // Inizio PLC effettivo.

        //***** Invio File *********************************************************************
        System.out.println("Sending File Data...");
        if (FlgAnnulla) {
            parent.debug("Annullato in Invio File");
            return -109;
        }
        if  (!LoadFileSysARM()) { //'Invio File
            Transmission_Error (" Il microprocessore non mi ha risposto correttamente!");
            return -110;
        }
        pausa(500);

        //***** Stop Address Serial Flash Command *************************************************
        System.out.println("Stop Address Serial Flash Command");
        sTmp = "00FSTO" +EOT;
        sTmp = calcolaCheck(sTmp); //' STX + 00FSTO + CheckSum + ETX
        if (!WriteRemoteCommand(sTmp, 405, true, 10000)) {
            parent.debug("Carattere ACK non ricevuto!=");
            return -111;
        }

        //***** Load Application from Serial Flash *********************************************
        if (FlgTx != parent.HTML) {
            pausa(500); //'

            System.out.println("Load Application from Serial Flash");
            sTmp = "00FAPP" + EOT;
        }else{
            //'***** Restart *************************************************
            System.out.println("Final reset");
            pausa(500);
            sTmp = "00RESET";
        }

        sTmp =calcolaCheck(sTmp); // STX + 00FAPP + CheckSum + ETX
        if (!SendAndWaitRestart(sTmp, 1500, true)){
            Transmission_Error("Il microprocessore non mi ha risposto correttamente!");
            return -112;
        }

        if (FlgTx != parent.HTML) {
            System.out.println("Final reset");
            sTmp = "00RESET";
            sTmp = calcolaCheck(sTmp); // STX + 00FAPP + CheckSum + ETX

            if (!SendAndWaitRestart(sTmp, 1500, true)) {
                Transmission_Error("Il microprocessore non mi ha risposto correttamente!");
                return -113;
            }
        }

        if (FlgAnnulla) {
            parent.debug("interotto in chiusura");
            return -114;
        }
        Termina();                  // "Programmazione completata!"
        if (!FlgLoadAppli) {
            Messaggio = "Programmazione completata!";

            if (FlgTx == parent.HTML) {
                Titolo = "Trasmetti Pagine HTML da REMOTO";// ' Trasmetti Pagine HTML da REMOTO
            } else
                Titolo = "trasmessa applicazione";
        } else {
            Messaggio = "";
            Titolo = "";
        }
        System.out.println(Messaggio);

        FlgOK = true ;
        FlgClose = true;

        return 0;
    }

}
/*
 resettato ed ? ripartito
1734104593496:creaConnessione: crea il socket [192.168.2.224] port:80
1734104593496:creaConnessione: attesa riconnessione tentativo: 0
1734104599167:Riconnessione avvenuta in tntativi: 1
1734104599168:WriteRemoteCommand: comando inviato: 00RESETE3


*/