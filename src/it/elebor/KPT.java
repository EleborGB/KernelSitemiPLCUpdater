package it.elebor;
/**
 * kernel protocol tester
 *
 * /opt/ibm/java-x86_64-80/jre/bin/java -jar kernelSistemiProtocol.jar 192.168.2.234 R 1156,4218
 *
 * @todo
 * - aggiungere la possibilità di leggere piu indirizzi separati da virgola
 * - aggiungere switch -c per leggere unfile di testo nel seguente formato
 * riga 1 insirizzo PL
 * riga 2 numero ripetizioni
 * riga 3 tempo attesa in decimi
 * riga 4 cmd, addr [,val]
 * ...
 * riga n cmd, addr [,val]
 *
 * ex.:
 * 192.168.2.234
 * 10
 * 5
 * R,4100
 * R,4101
 * W,4200,50
 *
 * descrizione:
 * esegue 10 volte con un ritardo di 0,5 sec quanto segue:
 * lettura registro 4100
 * lettura registro 4101
 * scrittura del valore 50 registro 4200
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class KPT {

    private boolean debug= false;// =true;//

    static final char PLC = 1;
    static final char HTML = 2;

    public static void  main(String[] args) {
        new KPT( args);
    }


    public void help(){
        System.out.println("\nKPT usage: \n" +
                             "----------\n\n" +
                        "\tjava -jar kernelSistemiProtocol.jar KPT -f objFile ipAddr\n"+
                        "or\n"+
                        "\tjava -jar kernelSistemiProtocol.jar KPT -f ipAddr cmd dataAddress[,dataAddress[,...]] [val[,val[,...]]]\n"+
                        "\nopt         :\n" +
                        "             -r[t[,d]] : repeat t=times to repeat the command cmd, d= delay  between cmd execution in tenth of second (default 1sec)\n"+
                        "             -f update application\n"+
                        "objFile     : the file name of the objcode\n"+
                        "ipAddr      : PLC ip address\n"+
                        "cmd         : R,W . type of command: R = read data , W = write data\n"+
                        "dataAddress : data address to be read or written \n"+
                        "val         : value to be written\n\n"+
                        "Examples:\n"+
                        "to update the project:\n\t> java -jar kernelSistemiProtocol.jar KPT -f user.s20 192.168.2.224\n"+
                        "to read DATA.32:\n\t> java -jar kernelSistemiProtocol.jar KPT -r 192.168.2.234 R 32\n" +
                        "to wite value 100 in DATA.4100:\n\t> java -jar kernelSistemiProtocol.jar KPT -r 192.168.2.234 W 4100 100\n"
        );
    }

    public  KPT(String[] args) {
        // write your code here
        boolean repeat=false;
        int t=0;    // infinite
        int d=1;    // 1 sec
        boolean cmd=false; //read
        String ipAddr="127.0.0.1";

        int val=0;

        // se la riga di comando contiene emno di due argomenti
        if (args.length<2){
            help();             //mostra l'aiuto
            System.exit(-1);    //e termina
        }

        //almeno 2 argomenti
        // se contiene il file del progetto da inviare al PLC
        if (args[0].contains("-f")) {
            /*
            se nel file da inviare ex: app.s20
            dopo la stringa
            S70500000000FA
            la riga successiva inizia inizia con
            S1400000
             è un terminale grafico
            */

            File src= new File(args[1]);
            if (!src.exists()) {
                System.out.println("The source file doesn't exist. Path=" + src.getAbsolutePath());
                System.exit(-1);
            }
            System.out.println("Read the Source  file.");

            ArrayList<String> linee= new ArrayList();

            int totLinee=0;
            boolean controllaSeGrafico=false;
            boolean terminaleGrafico=false;
            try {
                BufferedReader reader = new BufferedReader(new FileReader(src));
                String currentLine;
                while((currentLine= reader.readLine())!=null){
                    if (controllaSeGrafico&&(currentLine.equals("S1400000")))
                        terminaleGrafico=true;
                    if (currentLine.equals("S70500000000FA"))
                        controllaSeGrafico=true;
                    linee.add(currentLine);
                    totLinee++;
                }
                reader.close();
            } catch (Exception ex){
                ex.printStackTrace();
                System.exit(-2);
            }

            debug("Lette " + totLinee + " linee.");
            //invia file
            int ret=0;
            if (!terminaleGrafico) { //se  non è un termianl grafica
                kernelUploaderEthernetTestuali kuet = new kernelUploaderEthernetTestuali(this,linee , args[2]);
                ret = kuet.upload();
            } else {
                KernelUploaderEthernetGrafici kueg = new KernelUploaderEthernetGrafici(this, linee, args[2]);
                ret = kueg.upload();
            }
            System.out.println("Upload return code:" + ret);
            System.exit(ret); //termina
        } else if ( args.length<3){
            help();
            System.exit(-1);
        }

        int k=0;

        if (args[0].contains("-r")) {
            repeat=true;
            //default values
            t=9999; //t= nu<mer of times to reppeat commadn
            d=10;   //delay
            k++;    //skip the first param
            String settings=args[0].replace("-r","");
            if (!settings.equals("")){
                if (settings.contains(",")){
                    String []setting=settings.split(",");
                    t=Integer.parseInt(setting[0]);
                    d=Integer.parseInt(setting[1]);
                } else {
                    t=Integer.parseInt(settings);
                }
            }
        }

        System.out.println("Following mode enabled: "+repeat);

        ipAddr=args[k];
        System.out.println("PLC address= "+ipAddr);

        if (args[k+1].toUpperCase().equals("W")){
            cmd=true;
            val=Integer.parseInt(args[k+3]);
            System.out.println("PLC comando= "+args[k+1]+ ": Scrittura");
        } else
            System.out.println("PLC comando= "+args[k+1]+": Lettura");

        System.out.println("PLC Indirizzi="+args[k+2]);

        KernelMonitor km= new KernelMonitor(this,ipAddr);

        try {
            if (args[k+2].contains(",")){
                String s[]=args[k+2].split(",");
                int data_Addresses[]= new int[s.length];
                for (int iter=0;iter<s.length;iter++)
                    data_Addresses[iter]=Integer.parseInt(s[iter]);

                km.run(cmd,data_Addresses,val,repeat,t,d);
            }
            else {
                int dataAddr[]= new int[1];
                dataAddr[0] = Integer.parseInt(args[k + 2]);
                km.run( cmd, dataAddr, val);
            }

        } catch (Exception ex){
            System.out.println("Data address not good");
            ex.printStackTrace();
            System.exit (-1);
        }

    }


    /**
     * ancillary function for debugging
     * @param s
     */
    public void debug(String s) {
        if (debug)
            System.out.println(System.currentTimeMillis()+":"+s);
    }

}
