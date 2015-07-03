package distributionWork;

import beanstalk.BeanstalkClient;
import beanstalk.BeanstalkException;
import beanstalk.BeanstalkJob;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.ArrayList;

/**
 * @auhtor: Carlos Marin Fernandez
 * @auhtor: Victor Font de la Rica
 * @date: 18-may-2015
 * @version: 1.0 
 * @description: Estructura principal del problema. Inicia las tuberías por donde recorrerán
 * los mensajes y tokens entre los nodos, envía los mensajes y los interpreta. 
 * Dijkstra-Scholten distributed termination algorithms.
 */
abstract class Nodo {    
    //Identificador de cada nodo
    private int ID;
    //Nodos de los que puede recibir o a los que puede enviar
    private ArrayList<Integer> puedoRecibirDe = new ArrayList<>();
    private ArrayList<Integer> puedoEnviarA = new ArrayList<>();
    //Check if the node has finished
    private volatile boolean finish = false;
    
    //Variables Neilsen-Mizuno Algoritmo Paso de Tokens
    //xS := x Solicitud (request) ; xE := x Envio (send)
    public PasoTestigo[] tokens;
    
    //Variables Dijkstra-Scholten
    private int[] InDeficit;
    private int inDeficit = 0;
    private int outDeficit = 0;
    private int parent = -1;
    public boolean isTerminated = true;
    Semaphore mutexDijkstraScholten = new Semaphore(1);
    
    public Nodo (int id, int max, boolean primera) {
        try {
            this.ID = id;
            this.InDeficit = new int[max];
            tokens = new PasoTestigo[3];
            tokens[0] = new PasoTestigo(this, "contadorS", "contadorE");
            tokens[1] = new PasoTestigo(this, "imgS", "imgE");
            tokens[2] = new PasoTestigo(this, "arbolS", "arbolE");
            initTuberias(primera);
        } catch (IOException ex) { System.out.println("ERROR Constructor de Nodo"); }
    }
    
    //Identificacion del nodo
    public int getID() {
        return this.ID;
    }
    
    //Lista de nodos a los que puede enviar
    public ArrayList<Integer> getNodosPuedoEnviar() {
        return this.puedoEnviarA;
    }
    
    //Valor del outdeficit para saber si se puede parar de trabajar
    public int getOutDeficit() {
        return this.outDeficit;
    }
    
    //Devuelve el padre del nodo para el algoritmo de distribucion distribuida
    public int getParent() {
        return this.parent;
    }
    
    //Actualiza el padre del nodo para el algoritmo de distribucion distribuida
    public void setParent(int newParent) {
        this.parent = newParent;
    }
    
    protected abstract void decodificarMS(String message);
    
    protected final void initTuberias(boolean primera) throws IOException {
        FileReader lectorLineas = new FileReader(EntornoRaiz.grafoInicial);
        BufferedReader linea = new BufferedReader(lectorLineas);
        
        linea.readLine(); //"digraph G {" es inútil
        String aresta = linea.readLine(); //Leemos todas las "N -> M" 
        while (!aresta.equals("}")) {
            String[] numbers = aresta.split("->"); //"N -> M" -> [N][M] 
            numbers[0] = numbers[0].trim(); //Quitamos espacios en blanco
            numbers[1] = numbers[1].trim();
            //Si N = ID -> ID puede enviar a M
            if (Integer.parseInt(numbers[0]) == this.ID) this.puedoEnviarA.add(Integer.parseInt(numbers[1]));
            //Si M = ID -> ID puede recibir de N
            if (Integer.parseInt(numbers[1]) == this.ID) this.puedoRecibirDe.add(Integer.parseInt(numbers[0]));
            aresta = linea.readLine();
        }
        //Cerramos flujo y fichero
        linea.close();
        lectorLineas.close();

        //2. Hacer que cada proceso (o hilo) imprima a qué nodos está conectado:
        //a quién puede enviar mensajes, y de quién puede recibir (es un grafo dirigido).
        if(primera) {
            System.out.print("El nodo " + this.ID + " puede enviar trabajos a nodo(s): ");
            for(Integer nodosEnviar : this.puedoEnviarA) { System.out.print(nodosEnviar + ", "); }
            if(this.puedoEnviarA.isEmpty()) System.out.print("Ningún nodo, ");
            if(!this.puedoRecibirDe.isEmpty()) System.out.print("\b\b y recibir de: ");
            for(Integer nodosRecibir : this.puedoRecibirDe) { System.out.print(nodosRecibir + ", "); }
            System.out.println("\b\b.");
        }
    }
    
    public void enviarMS(String mensaje, int idReceptor) {
        BeanstalkClient client = new BeanstalkClient("127.0.0.1", 15000);
        try {
            //Selección de Tubo por ID y colocamos la información
            client.useTube("t_" + idReceptor);
            client.put(0, 0, 0, mensaje.getBytes());
            String[] sms = mensaje.split("/");
            
            //Añadimos un trabajo más al nodo
            if(sms[1].charAt(sms[1].length()-1) == 'T') {
                this.mutexDijkstraScholten.acquire();
                this.outDeficit++; //Increment outDeficit
                this.mutexDijkstraScholten.release();
            }
        } catch (BeanstalkException | InterruptedException ex) { System.out.println("ERROR en enviarMS: " + ex.getMessage()); }
        finally { client.close(); }
    }
    
    //Incrementa el array de inDeficit en la posición idx y la variable inDeficit en uno.
    public void incrementInDeficit(int idx) {
        this.InDeficit[idx]++;
        this.inDeficit++;
    }
    
    //Decrementa el outDeficit en uno.
    public void decrementOutDeficit() {
        this.outDeficit--;
    }
    
    //Le damos los holdings de los tokens
    public void setHolding(PasoTestigo token, boolean isHolding) {
        token.setHolding(isHolding);
    }
    
    //Formato de los mensajes enviados entre nodos 
    //Formato: [IDemisor]/[TipoMS]/[IDreceptor](/[extra])
    public void crearAndEnviarMS(String tipo, int destino, String[] extra) {
        String mensaje = this.ID + "/" + tipo + "/" + destino;
        if (extra != null) for (String atributo : extra) mensaje += ("/" + atributo);
        enviarMS(mensaje, destino);
    }
    
    protected void enviarContenidoTuberias() {
        BeanstalkClient clientListener = new BeanstalkClient("127.0.0.1", 15000);
        BeanstalkJob work;
        try {
            //Observar el contenido de las tuberías del nodo que lo invoca
            clientListener.watchTube("t_" + this.getID());
            while (!this.finish) {
                do {
                    work = clientListener.reserve(null);
                } while (work == null); //Trabajo bloqueado indefinidamente
                //Eliminamos el trabajo del tubo
                clientListener.deleteJob(work); 
                decodificarMS(new String(work.getData()));
            }
        } catch (BeanstalkException ex) { 
            System.out.println("ERROR en enviarContenidoTuberias de Node.java: " + ex.getMessage()); 
        } finally { clientListener.close(); }
    }
    
    /**
     * @see "send Signal" Algoritmo de Dijkstra-Scholten
     */
    public void sendSignal() {
        try {
            this.mutexDijkstraScholten.acquire(); //Wait
            while (this.inDeficit > 1) { //Mientras queden trabajos pendientes
                for (Integer index : this.puedoRecibirDe) {
                    if ((this.InDeficit[index] > 1) || (this.InDeficit[index] == 1 && index != this.parent)) {
                        this.crearAndEnviarMS("signal", index, null);
                        this.InDeficit[index]--;
                        this.inDeficit--;
                    }
                }
            }
            //No trabajos pendientes a niveles inferiores
            if (this.inDeficit == 1 && this.isTerminated && this.outDeficit == 0) {
                this.crearAndEnviarMS("signal", this.parent, null);
                this.InDeficit[this.parent] = 0;
                this.inDeficit = 0;
                this.parent = -1;
            }
            this.mutexDijkstraScholten.release(); //Signal
        } catch (InterruptedException ex) { System.out.println("ERROR en sendSignal de Node.java: " + ex.getMessage()); }
    }
    
    //PasoTestigo metodos
    //Entrada a la sección critica del algoritmo de envio de tokens.
    public void preSC(PasoTestigo token) throws InterruptedException {
        token.preprotocolo();
    }
    
    //Salida a la sección critica del algoritmo de envio de tokens.
    public void postSC(PasoTestigo token) throws InterruptedException {
        token.postprotocolo();
    }
    
    //Nos devuelve el padre del PasoTestigo.
    public int getTokenParent(PasoTestigo token) {
        return token.getParent();
    }

    //Modificamos el padre del PasoTestigo.
    public void setTokenParent(PasoTestigo token, int nP) {
        token.setParent(nP);
    }
    
    //Interprete de mensajes de envio de los tokens
    public void decodificadorMS(PasoTestigo token, String[] sms) {
        try {
            token.Recieve(sms);
        } catch (InterruptedException ex) { }
    }
    
    public void setFinish() {
        this.finish = true;
    }
}