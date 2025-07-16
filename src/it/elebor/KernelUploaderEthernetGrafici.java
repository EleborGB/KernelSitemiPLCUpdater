package it.elebor;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by glauco on 12/12/2024.
 */
public class KernelUploaderEthernetGrafici extends KernelEthernet{



    boolean FlgTxPages=true;
    boolean FlgTxPLC=true;




    int NumColors=2;

    ArrayList<String> linee;

    public KernelUploaderEthernetGrafici(KPT parent, ArrayList linee, String ipAddress) {
        super (parent,ipAddress);
        this.linee = linee;
        ipAddr = ipAddress;

    }


    public String AttesaRS232Stringa(String CheckRisp, boolean b, int wait){
        return "";
    }









    public int upload(){
        int pMaxAddr=0;
        try{


            boolean FlgPageCan = false, FlgAppliCan = false;    // inizializzo i flags
            int pMinAddr = 0 ;                                  // Inizializzo l'indirizzo BASSO
            int ValByteTotali = pMaxAddr;                        // Numero totale di byte da trasmettere
            int ValByteTX = 0 , S8_Count = 0 ;                  // Azzero i dati
            String FileLine;

            for(int iter=0;iter<linee.size();iter++) {
                if (FlgAnnulla) {
                    parent.debug("flag annulla ");
                    return -3;
                }
                ValByteTX++;
                FileLine = linee.get(iter);
                parent.debug("Riga file: "+iter+" = "+FileLine);
                String CompareStr;
                StringBuffer protoCompareStr = new StringBuffer();
                for (int iter2 = 2; iter2 < FileLine.length(); iter2 += 2) // Converto ogni Byte della riga in una sequenza di caratteri...
                {
                    String chunk = FileLine.substring(iter2, iter2+2); //legge 2 byte
                    //System.out.println("chunk *"+chunk+"* iter2="+iter2);
                    int c = Integer.parseInt(chunk, 16);
                    protoCompareStr.append((char) c);
                }
                CompareStr = protoCompareStr.toString();
                //System.out.println("CompareStr: "+CompareStr);
                // Se nella sequenza e' presente la parola "Pages" vuol dire che in quel punto inizia la parte compilata delle Pagine
                if (((CompareStr.contains("Pages")) && (FlgTxPages == true) && (Terminale.FlgInvPagPlc == true))
                        || (CompareStr.contains("Set_Up") == true)
                        || (CompareStr.contains("Fonts") == true)) {

                    // Partenza Fonts : Trasmettere il file con i FONT ?
                    if (CompareStr.contains("Fonts") == true) {
                        Transmission_Error("Non deve inviare i font");
                        return -4;
                    }
                    //if ((CompareStr.contains("Set_Up") && (FlgTxPLC = False)) {
                    //    Call Transmission_Error("nondeve inviare setup") ;
                    //    return -5;                                  //... ed esco!
                    //}
                    if (!creaConnessione()) {
                        return -6;
                    }

                    if (FlgAnnulla == true) {
                        parent.debug(" flag annulla in trasmissione");
                        return -7;
                    }
                    //***************** Comando "MENU" ************************************
                    parent.debug("Attesa consenso al Trasferimento...");
                    String sTmp = "00MENU";
                    sTmp = calcolaCheck(sTmp); // STX + 00FERA + CheckSum + ETX
                    if (SendAndWaitRestart(sTmp, 5000, true) == false) {
                        Transmission_Error("Sincronizzazione non riuscita!"); //" segnalo l' errore..."Sincronizzazione non riuscita!"
                        return -8; // ... ed esco dalla routine!
                    }
                    //****** Comando "menu" x controllare che sia all'interno del menu ****

                    for (int i = 1; i < 11; i++) {
                        parent.debug("Attendere prego.... " + i);
                        if (FlgAnnulla == true) {
                            parent.debug("annullato in menu");
                            return -9;
                        }
                        if (i >= 10) { // Se dopo x tentativi non ho ricevuto l' "ok"...
                            Transmission_Error("Il microprocessore non mi ha risposto correttamente!");
                            return -10;
                        } // Invio il comando "menu" e attendo la risposta "ok"

                        sTmp = calcolaCheck("00menu"); // STX + 00FERA + CheckSum + ETX
                        parent.debug("Invia comando 00menu per verificare la connessione");
                        if (WriteRemoteCommand(sTmp, 0, false, 5000) == false) {
                            parent.debug("Il microprocessore non mi ha risposto correttamente!");
                            return -11;
                        } // ... segnalo l' errore...

                        if (Risposta.contains("ok")) {
                            parent.debug("Riposta= ok interrompe il ciclo for");
                            break;
                        }
                    }
                    if (Terminale.FlgInvPagPlc == true) {              // Se non c'e l'inversione tra Pagine e PLC...
                        if (CompareStr.contains("Pages")) {    // ' Partenza Pagine
                            parent.debug("Pages: Richiesta Download...");
                            pausa(500);// 'Evita che la programmazione parta con il Beeper acceso!

                            //'***************** Attesa consenso al Trasferimento ***************************
                            parent.debug("Attesa cancellazione flash...");
                            sTmp = "00CLF";
                            sTmp = calcolaCheck(sTmp);// ' STX + 00CLF + CheckSum + ETX

                            if (!WriteRemoteCommand(sTmp, 0, false, 5000)) {
                                return -12;
                            }
                            int TimeToWait = 15;
                            for (int i = 0; i < TimeToWait; i++) {
                                parent.debug("Attesa cancellazione flash... "+TimeToWait+" sec. :" + i);
                                if (FlgAnnulla) {
                                    parent.debug("flag annulla in cancellazione");
                                    return -13;
                                }
                                if (i >= TimeToWait) {
                                    //Se dopo TimeToWait secondi non ho ricevuto l' ACK...
                                    Transmission_Error("Cancellazione flash non eseguita!");
                                    return -14;
                                }
                            /*

                                If ClientSocket IsNot Nothing Then
                                    Call ClientSocket.Close()
                                    ClientSocket = Nothing
                                End If
                                ' Cerca quando il PLC si è resettato ed è ripartito
                                Dim FlgRestart As Boolean = False
                                Try
                                    Call ConnessioneRemota(False)
                                    FlgRestart = True
                                Catch ex As Exception
                                    ' Non fa nulla....
                                End Try
                                If FlgRestart = True Then
                                    ' Invio il comando "clf" e attendo la risposta Char "ok"
                                    sTmp = "00clf"
                                    sTmp = Convert.ToChar(STX) & sTmp & CalcolaCheck(sTmp) & Convert.ToChar(ETX) ' STX + 00FERA + CheckSum + ETX
                                    If WriteRemoteCommand(LabelStato, 0, False, 1000) = False Then Exit Sub ' ... segnalo l'errore ... "Il microprocessore non mi ha risposto correttamente!"
                                    If Risposta.Contains("ok") = True Then Exit For
                                    Call System.Threading.Thread.Sleep(DELAY_TIME)
                                End If
                             */


                                // Cerca quando il PLC si è resettato ed è ripartito

                                if (creaConnessione()) {
                                    //' Invio il comando "clf" e attendo la risposta Char "ok"
                                    sTmp = "00clf";
                                    sTmp =  calcolaCheck(sTmp) ;
                                    // STX + 00FERA + CheckSum + ETX
                                    if (!WriteRemoteCommand(sTmp, 0, false, 1000)) {
                                        parent.debug("Il microprocessore non mi ha risposto correttamente!");
                                        return -15;
                                    }
                                    if (Risposta.contains("ok")) {
                                        i=TimeToWait;
                                        break;
                                    }
                                    pausa(5000L);
                                }

                            }
                            parent.debug("Caricamento Applicazione.....");
                            FlgPageCan = true;
                            FlgAppliCan = false;// Inizio Pagine effettive.
                        }
                    }
                    if (FlgAnnulla == true) {

                        parent.debug("flag annulla in trasmissione");
                        return -16;
                    }
                    if ((CompareStr.contains("Set_Up")) && (FlgTxPLC == true)) {        // Partenza Set Up
                        //***** Clear Serial Flash Command *****************************************************

                        sTmp = "00FERA" + EOT;
                        sTmp =  calcolaCheck(sTmp) ; // ' STX + 00FERA + CheckSum + ETX
                        parent.debug("Clear Serial Flash Command");
                        if (!WriteRemoteCommand(sTmp, 415, true, 20000)) {
                            //Reader.Close() '... chiudo il file ...
                            parent.debug(" Flash Error! Programmazione Interrotta!");
                            return -17;
                        }
                        //***** Unprotect Serial Flash Command *************************************************
                        parent.debug("Unprotect Serial Flash Command");
                        sTmp = "00FUNP" + EOT;
                        sTmp =  calcolaCheck(sTmp);// ' STX + 00FUNP + CheckSum + ETX
                        if (!WriteRemoteCommand(sTmp, 317, true, 10000)) {
                            parent.debug("Unprotect Serial Flash Command\n" + " PLC non Risponde!");
                            return -18;
                        }
                        FlgPageCan = false;
                        FlgAppliCan = true;         // Inizio PLC effettivo.
                    }
                }
                if (FlgAnnulla) {
                    parent.debug("flag annulla in invio");

                    return -18;
                }
                if (FlgPageCan) {
                    String sTmp = calcolaCheck("00" + FileLine) ;
                    int s;
                    for (s = 0; s < 3; s++) {
                        parent.debug("invia pagine FlgPageCan=true");
                        if (!WriteRemoteCommand(sTmp, 0, false, 3000)) {
                            parent.debug("Il microprocessore non mi ha risposto correttamente!");
                            return -19;
                        }
                        if (!verificaByte(Risposta, ACK)) {
                            if (!SendAndWaitRestart(sTmp, 0, false)) {
                                if (FlgAnnulla) {

                                    parent.debug("falg annulla in invio");
                                    return -20;
                                }
                                //Reader.Close() '... chiudo il file ...
                                Transmission_Error("Caricamento file pagine non riuscito!");// '... segnalo l' errore...

                                return -21;
                            } else {
                                break;
                            }
                        }
                    }
                    if (s >= 3) {
                        if (FlgAnnulla) {
                            parent.debug("annulla in trasmisisonefile");
                            return -22;
                        }
                        //Reader.Close() '... chiudo il file ...
                        Transmission_Error("Caricamento file pagine non riuscito!");// '... segnalo l' errore...
                        return -23; // ... ed esco!
                    }
                    String ck = FileLine.substring(1, 2);
                    if (ck.equals("7") || ck.equals("8") || ck.equals("9")) { //' invia la linea, aspetta l'ACK e termina la parte "Pagine"
                        FlgPageCan = false;// ' Fine della parte delle Pagine
                    }

                    //If FileLine.Substring(1, 2) = "10" Then FlgPageCan = False ' Se incontro una riga "S10" vuol dire che e' finita la parte delle pagine
                    if ((!FlgPageCan) && (!FlgTxPLC)) {
                        break; //Exit Do
                    }
                }
                if (FlgAnnulla) {

                    parent.debug("flag annulla");
                    return -24;
                }

                boolean FlgIgnora = false;
                if (FlgAppliCan) {
                    int Addr;
                    String ck = FileLine.substring(1, 2);

                    if (ck.equals("1"))
                        Addr = Integer.parseInt(FileLine.substring(4, 8), 16); // Indirizzo a 4 char
                    else if (ck.equals("2"))
                        Addr = Integer.parseInt(FileLine.substring(4, 10), 16); //' Indirizzo a 6 char
                    else if (ck.equals("3"))
                        Addr = Integer.parseInt(FileLine.substring(4, 12), 16); //' Indirizzo a 8 char
                    else if ((ck.equals("7")) || (ck.equals("8")) || (ck.equals("9"))) { // Fine del File
                        Addr =SYSTEM_START_200;
                        if(Terminale.Microprocessore.toUpperCase().contains("ARM,SERIES_100"))
                            Addr=SYSTEM_START_100;

                        FlgIgnora = true;
                        //***** Stop Address Serial Flash Command *************************************************
                        parent.debug("Stop Address Serial Flash Command");
                        String sTmp = "00FSTO" + EOT;
                        sTmp =  calcolaCheck(sTmp) ; //' STX + 00FSTO + CheckSum + ETX
                        parent.debug("Stop Address Serial Flash Command");
                        if (!WriteRemoteCommand(sTmp, 405, true, 10000)) {

                            parent.debug(" Carattere ACK non ricevuto!");
                            return -25;
                        }
                        //***** Load Application from Serial Flash *********************************************
                        parent.debug("Load Application from Serial Flash");
                        sTmp = "00FAPP" + EOT;
                        sTmp =  calcolaCheck(sTmp) ; // STX + 00FC + CheckSum + ETX
                        if (!SendAndWaitRestart(sTmp, 15000, true)) {
                            Transmission_Error("Sincronizzazione non riuscita!"); // segnalo l' errore...
                            return -26;
                        }
                        FlgAppliCan = false;
                    } else {
                        Addr = 0;
                    }
                    if ((Addr > 0) && (!FlgIgnora)) {

                        String StrToSend = "00F" + FileLine.substring(4, FileLine.length() - 6) + EOT;
                        String sTmp =  calcolaCheck(StrToSend) ; //' STX + 00FC + CheckSum + ETX
                        int s;
                        for (s = 0; s < 3; s++) {
                            parent.debug(" Invio dati in corso... ");
                            if (!WriteRemoteCommand(sTmp, 0, false, 3000)) {

                                parent.debug("Il microprocessore non mi ha risposto correttamente!");
                                return -27;
                            }
                            if (!verificaByte(Risposta, ACK)) {
                                parent.debug("Reinvio Invio dati in corso... ");
                                if (!SendAndWaitRestart(sTmp, 0, false)) {
                                    if (FlgAnnulla) {

                                        parent.debug("flag annulla in invio dati in corso");
                                        return -28;
                                    }
                                    //Reader.Close()                         '... chiudo il file ...
                                    Transmission_Error("Caricamento file pagine non riuscito!"); //'... segnalo l'errore ...
                                    return -29;
                                    //... ed esco!
                                }
                            } else {
                                break;
                            }
                        }
                        if (s >= 3) {
                            if (FlgAnnulla) {
                                parent.debug("flag annulla in invio");
                                return -30;
                            }
                            // Reader.Close()                         '... chiudo il file ...
                            Transmission_Error("Carattere ACK non ricevuto!");// '... segnalo l'errore ...
                            return -31;//Exit Sub                                    '... ed esco!
                        }
                    }
                }

                if (FlgAnnulla) {
                    parent.debug("flag annulla in qualcosa");
                    return -32;
                }
            } //loop

            if (NumColors == 65536) {
                String sTmp = "00RESET";
                sTmp =  calcolaCheck(sTmp); // STX + 00FERA + CheckSum + ETX
                if (!WriteRemoteCommand(sTmp, 0, false, 500)) {
                    parent.debug("Il microprocessore non mi ha risposto correttamente!");
                    return -40;
                } //' ... segnalo l'errore ...
            }
            ValByteTX = ValByteTotali; // e metto il valore in % = al valore massimo
            //LabelStato ="Programmazione completata!";
            Termina() ;                     // Termina la comunicazione seriale
            Messaggio = "Programmazione completata!";
            Titolo ="Programmazione completata!";
            FlgOK = true;
            FlgClose = true;
        } catch (Exception ex){
            ex.printStackTrace();
            parent.debug("*** catch globale ***");

            // "Procedura Interrotta!" , "Stop"
            Termina();// ' Termina la comunicazione seriale

            //Messaggio = ResourcesLang(316):
            //Titolo = ResourcesLang(191)

            FlgOK = false;
            FlgClose = true;
        }
        finally {
            if (ClientSocket !=null) {
                try {
                    ClientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ClientSocket = null;
            }
        }
        return 0;
    }

    public boolean verificaByte(String Risposta,char c){
        byte[] v=Risposta.getBytes();
        for(int i=0;i<v.length;i++){
            if (v[i]==c)
                return true;
        }
        return false;
    }



}
