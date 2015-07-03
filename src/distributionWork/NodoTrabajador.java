package distributionWork;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * @auhtor: Carlos Marin Fernandez
 * @auhtor: Victor Font de la Rica
 * @date: 18-may-2015
 * @version: 1.0 
 * @description: Nodo al cual dependiendo del mensaje que le llega por la cola de 
 * mensajes, envía o recibe un token, o realiza el trabajo el cual se le ha mandado.
 */
public class NodoTrabajador extends Nodo implements Runnable {
    //Almacena mensajes para invocarlos desde cada trabajo
    private final ArrayBlockingQueue internalMailBox = new ArrayBlockingQueue(5000);

    //Se ha escogido el padre de la exclusión mutua
    private volatile boolean contadorEM = false;
    private volatile boolean imgEM = false;
    private volatile boolean arbolEM = false;

    private volatile String tipoTrabajo = "";
    
    /**
     * @param id identificador Nodo
     * @param max Maximos nodos existentes
     * @param primera
     */
    public NodoTrabajador(int id, int max, boolean primera) {
        super(id, max, primera);
    }

    //Interpreta los mensajes que recibe.
    @Override
    protected void decodificarMS(String message) {
        //El mensaje este delimitado por '/'
        String[] sms = message.split("/");
        int idNodo = Integer.parseInt(sms[0]);
        String tipoMensaje = sms[1];
        tipoTrabajo = tipoMensaje;
        if (tipoMensaje.contains("T")) { //Tipo = trabajo
            boolean changeParent = false;
            try {
                this.mutexDijkstraScholten.acquire();
                this.isTerminated = false; //No puede terminar si tiene trabajos a cargo
                changeParent = this.setParents(idNodo);
                this.incrementInDeficit(idNodo);
                this.mutexDijkstraScholten.release();
            } catch (InterruptedException ex) { System.out.println("ERROR en decodificarMS de NoRaiz.java (tipo de mensaje "
                    + "= Trabajo): " + ex.getMessage());}
            this.sendSignal();
            this.internalMailBox.add(sms); //Add a la cola de mensajes
            if (changeParent) this.internalMailBox.add(new String[]{Integer.toString(this.getID()), "arbolT", Integer.toString(idNodo)});
        }
        
        //Segun el tipo de mensaje actuamos en consecuencia (trabajo activo)
        switch (tipoMensaje) {
            //Solicitud de recepcion de token
            case "contadorS": this.decodificadorMS(this.tokens[0], sms); break;
            case "imgS": this.decodificadorMS(this.tokens[1], sms); break;
            case "arbolS": this.decodificadorMS(this.tokens[2], sms); break;
                
            //Envio de token
            case "contadorE": this.tokens[0].getToken(); break;
            case "imgE": this.tokens[1].getToken(); break;
            case "arbolE": this.tokens[2].getToken(); break;
        }
        
        //Terminacion parcial o total de trabajo
        if(tipoMensaje.equals("signal")) {
            try {
                this.mutexDijkstraScholten.acquire();
                this.decrementOutDeficit();
                this.mutexDijkstraScholten.release();
                this.sendSignal();
            } catch (InterruptedException ex) { System.out.println("ERROR en decodificarMS de NoRaiz.java (tipo de mensaje "
                    + "= signal): " + ex.getMessage()); }
        } else if (tipoMensaje.equals("fin")) this.setFinish();
    }

    @Override
    public void run() {
        Thread t = new Thread(new TrabajoNodo(this.internalMailBox, this));
        t.start();
        this.enviarContenidoTuberias();
        this.internalMailBox.add(new String[]{"trabajoFin"});
        try {
            t.join();
        } catch (InterruptedException ex) { System.out.println("ERROR en run del NoRaiz.java: " + ex.getMessage()); }
    }

    //Actualiza los parents del nodo
    private boolean setParents(int source) {
        //Dependiendo del tipo de envio de Token que llame al método 
        switch(tipoTrabajo.charAt(0)) {
            case 'c': //contador
                if (this.getTokenParent(this.tokens[0]) == -1 && contadorEM == false) {
                    this.setTokenParent(this.tokens[0], source);
                    contadorEM = true;
                }
                break;
            case 'i': //img
                if (this.getTokenParent(this.tokens[1]) == -1 && imgEM == false) {
                    this.setTokenParent(this.tokens[1], source);
                    imgEM = true;
                }
                break;    
        }
        if (this.getTokenParent(this.tokens[2]) == -1 && arbolEM == false) {
            this.setTokenParent(this.tokens[2], source);
            arbolEM = true;
        }
        
        if(this.getParent() == -1) {
            this.setParent(source);
            return true;
        } else return false;
    }
    
    public void setTerminated() {
        try {
            this.mutexDijkstraScholten.acquire();
            this.isTerminated = true;
            this.mutexDijkstraScholten.release();
        } catch (InterruptedException ex) { System.out.println("ERROR en setTerminated de NoRaiz.java: " + ex.getMessage()); }
    }
}