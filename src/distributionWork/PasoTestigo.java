package distributionWork;

/**
 * @auhtor: Carlos Marin Fernandez
 * @auhtor: Victor Font de la Rica
 * @date: 19-may-2015
 * @version: 1.0 
 * @description: Neilsen-Mizuno token-passing algorithms and pre/post critical section
 */
import java.util.concurrent.Semaphore;

public class PasoTestigo {
    
    private Nodo Nodo;
    
    //Para conseguir la exclusión mutua
    private int parent = -1; //Initialized to form a tree
    private int deferred = -1;
    private boolean holding = false; //true if the root, false in others. Retiene el token.
    
    //Semáforos asegurar EM y el paso correcto de tokens
    private Semaphore semaforoTokens = new Semaphore(0);
    private Semaphore semaforoExMutua = new Semaphore(1);
    
    //Posibles mensajes a enviar
    private String Solicitud;
    private String Envio;

    /**
     * @param nodo Nodo al que pertenece
     * @param solicitud Tipo de ms de solicitud de token
     * @param envio Tipo de ms de envio de token
     */
    public PasoTestigo(Nodo nodo, String solicitud, String envio) {
        this.Nodo = nodo;
        this.Solicitud = solicitud;
        this.Envio = envio;
    }
    
    //Al recibir el token, se libera el semaforo.
    public void getToken() {
        this.semaforoTokens.release();
    }

    //Actualización del nuevo padre
    public void setParent(int newParent) {
        this.parent = newParent;
    }

    public int getParent() {
        return this.parent;
    }

    //Actualización de holding 
    public void setHolding(boolean isHolding) {
        this.holding = isHolding;
    }
    
    //Atributo del nuevo mensaje que se enviará
    private String[] sacarID(int id) {
        return new String[]{Integer.toString(id)};
    }

    //Entrada a la sección critica
    public void preprotocolo() throws InterruptedException {
        this.semaforoExMutua.acquire();
        if (!this.holding) { //Si no tiene el token, lo reclama
            //send(request, parent, myID, myID)
            Nodo.crearAndEnviarMS(this.Solicitud, this.parent, sacarID(Nodo.getID()));
            this.parent = -1;
            this.semaforoExMutua.release();
            this.semaforoTokens.acquire(); //Recieve(token)
            this.semaforoExMutua.acquire();
        }
        this.holding = false;
        this.semaforoExMutua.release();
    }

    //Salida a la sección critica
    public void postprotocolo() throws InterruptedException {
        this.semaforoExMutua.acquire();
        if (this.deferred != -1) {
            //send(token, deferred)
            Nodo.crearAndEnviarMS(this.Envio, this.deferred, null);
            this.deferred = -1;
        } else this.holding = true;
        this.semaforoExMutua.release();
    }
    
    /**
     * Interpreta la solicitud.
     * @param msInterpretar
     * @throws InterruptedException 
     * @see Recieve Algoritmo Neilsen-Mizuno
     */
    public void Recieve(String[] msInterpretar) throws InterruptedException {
        //recieve(request, source, originator)
        int IDnodoOrigen = Integer.parseInt(msInterpretar[3]);
        this.semaforoExMutua.acquire();
        if(this.parent == -1) { //No tiene padre
            if(this.holding) { //Al tener token lo enviará; (if holding)
                //send(token, originator)
                Nodo.crearAndEnviarMS(this.Envio, IDnodoOrigen, null);
                this.holding = false;
            } else this.deferred = IDnodoOrigen; //Al no tenerlo lo guarda en pendiente
        //Tiene padre, le envia una solicitud a su padre
        //send(request, parent, myID, originator)
        } else Nodo.crearAndEnviarMS(this.Solicitud, this.parent, sacarID(IDnodoOrigen)); 
        this.parent = Integer.parseInt(msInterpretar[0]);
        this.semaforoExMutua.release();
    }
}