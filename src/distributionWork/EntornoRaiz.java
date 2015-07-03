package distributionWork;

import beanstalk.BeanstalkClient;
import beanstalk.BeanstalkException;
import beanstalk.BeanstalkJob;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * @auhtor: Carlos Marin Fernandez
 * @auhtor: Victor Font de la Rica
 * @date: 20-may-2015
 * @version: 1.0 
 * @description: Nodo que generará el entorno del resto de los nodos e inicariá el resto de 
 * procesos en paralelo que utilizarán el beanstalkd como método de envío de mensajes. 
 */
public class EntornoRaiz extends Nodo {    
    //Número por defecto a contar
    private static final int cantidadTotalContar = 100000;
    private static final int totalNodos = 16; //En este caso, 17 - Raiz
    
    private static final Thread hilos[] = new Thread[totalNodos];
    
    //Archivos auxiliares (imagenes o grafo para repartir mensajes)
    public static final String imagenOriginal = "./images/imagenOriginal.jpg";
    public static final String imagenModificada = "./images/imagenModificada.png";
    public static final String digrafoArbol = "./digrafo.dot";
    public static final String ficheroTotales = "./totales.txt";
    public static final String grafoInicial = "./grafo.dot";
    
    //1. Implementar el nodo raíz
    public EntornoRaiz(int id, int max, boolean first) {
        super(id, max, false);
        //Al ser nodo raiz posee todos los tokens
        this.setHolding(this.tokens[0], true);
        this.setHolding(this.tokens[1], true);
        this.setHolding(this.tokens[2], true);
    }
    
    //1. la creación de los demás procesos (o hilos).
    private static void despertarNodosTrabajadores(boolean primera) {
        for (int i = 0; i < hilos.length; i++) {
            hilos[i] = new Thread(new NodoTrabajador(i + 1, totalNodos + 1, primera));
            hilos[i].start();
        }
    }
    
    @Override
    protected void decodificarMS(String message) {
        //El mensaje este delimitado por '/'
        String[] sms = message.split("/");
        if(sms[1].charAt(sms[1].length()-1) == 'S') { //Solicitud de recepcion de token
            if(sms[1].startsWith("contador")) this.decodificadorMS(this.tokens[0], sms); //Tipo contador
            else if(sms[1].startsWith("img")) this.decodificadorMS(this.tokens[1], sms); //Tipo imagen
            else this.decodificadorMS(this.tokens[2], sms); //Tipo construccion arbol
        } else { //Terminacion parcial o total de trabajo
            this.decrementOutDeficit();
            //Si outDeficit == 0 pueden acabar todos los nodos
            if (this.getOutDeficit() == 0) {
                for (int node = 1; node <= totalNodos;) this.crearAndEnviarMS("fin", node++, null);
                this.setFinish(); //Fin del nodo raiz
            }
        }
    }
    
    //Limpiamos las tuberias por si existen mensajes anteriores y borrarlos
    private static boolean limpiezaTubo() {
        BeanstalkClient clean = new BeanstalkClient("127.0.0.1", 15000);
        boolean heAcabado = false;
        System.out.print("Limpiando el interior de los tubos... ");
        try {
            for (int i = 0; i <= totalNodos; i++) {
                clean.watchTube("t_" + i);
                BeanstalkJob work = clean.reserve(0);
                while (work != null) { //Existe algun mensaje en el tubo work.
                    clean.deleteJob(work);
                    work = clean.reserve(0);
                }
            }
            heAcabado = true;
            System.out.println("OK");
        } catch (BeanstalkException ex) { System.out.println("ERROR en limpiezaTubo de EntornoRaiz.java: " + ex.getMessage()); }
        clean.close();
        return heAcabado;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {            
            if(!limpiezaTubo()) {
                System.out.println("Ha ocurrido un fallo en el uso del beanstalk, por favor, "
                        + " asegurese de \ntener ejecutando el daemon siguiente en el terminal: "
                        + "\nbeanstalkd -l 127.0.0.1 -p 15000");
                System.exit(0);
            }
            
            System.out.println("-------------------------------------------------------------");
            System.out.println("Información sobre los nodos: ");
            
            EntornoRaiz e = new EntornoRaiz(0, totalNodos+1, true);
            despertarNodosTrabajadores(true);
            System.out.println("-------------------------------------------------------------");
            System.out.print("Iniciando contador distribuido... ");
            
            //Inicializamos el fichero que contendrá el árbol de expansión
            FileWriter writer1 = new FileWriter(digrafoArbol);
            writer1.write("digraph G {\n");
            writer1.close();
            
            //Inicializamos el contador a 0
            writer1 = new FileWriter(ficheroTotales);
            writer1.write("0");
            writer1.close();
            
            long inicio = System.currentTimeMillis();
            e.divideCantidades(cantidadTotalContar);
            e.enviarContenidoTuberias();
            System.out.println("ARCHIEVED\nEl tiempo total en realizar el conteo hasta "+ cantidadTotalContar +
                    " ha sido de " + (float)(System.currentTimeMillis() - inicio) +" milisegundos.");
            System.out.println("\n\n\n");
            
            BufferedReader bi = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Desea ejecutar la parte opcional de la practica (aplicar\nun filtro Sepia"
                    + " sobre la imagen " +imagenOriginal+ ")? [S/N] --> ");
            if(bi.readLine().toLowerCase().charAt(0) == 's') {
                limpiezaTubo();
                System.out.println("-------------------------------------------------------------");
                writer1 = new FileWriter(digrafoArbol, false);
                writer1.write("digraph G {\n");
                writer1.close();
                e = new EntornoRaiz(0, totalNodos+1, false);
                despertarNodosTrabajadores(false);
                System.out.print("Iniciando aplicación filtro sepia... ");
                
                //Creamos la nueva imagen donde copiaremos pixel a pixel
                BufferedImage imgOriginal = ImageIO.read(new File(imagenOriginal));
                BufferedImage imgModificada = new BufferedImage(imgOriginal.getWidth(), imgOriginal.getHeight(), BufferedImage.TYPE_INT_RGB);
                ImageIO.write(imgModificada, "png", new File(imagenModificada));
                inicio = System.currentTimeMillis();
                e.divideTrabajoSepia(ImageIO.read(new File(imagenOriginal)).getHeight());
                e.enviarContenidoTuberias();
                System.out.println("ARCHIEVED\nEl tiempo total en aplicar el filtro de la nueva imagen "
                        + imagenModificada + " ha sido de " + (float)(System.currentTimeMillis() - inicio)/1000 +" segundos.");
            }
            //Acabamos con el fichero del arbol de expansion
            writer1 = new FileWriter(digrafoArbol, true);
            writer1.write("}");
            writer1.close();
        } catch (IOException ex) { }
    }
    
    //3. Desde el nodo de entorno enviar un “trabajo” de “contar”
    //Dividir el total a contar para cada nodo
    private void divideCantidades(int cantidadTotal) {
        //El total que tiene que contar cada hijo
        int c = cantidadTotal / this.getNodosPuedoEnviar().size();
        for (int i = 0; i < this.getNodosPuedoEnviar().size(); i++) {
            int nodo = this.getNodosPuedoEnviar().get(i);
            this.crearAndEnviarMS("contadorT", nodo, new String[]{Integer.toString(c)});
        }
    }
    
    //Dividir el trabajo de aplicar el filtro a contar para cada nodo
    private void divideTrabajoSepia(int altura) {
        int columna = 0;
        int cantidadTrabajo = altura / this.getNodosPuedoEnviar().size();
        for (int i = 0; i < this.getNodosPuedoEnviar().size(); i++) {
            int trabajador = this.getNodosPuedoEnviar().get(i);
            //Y columna donde trabajar; cantidad total de pixeles a tratar
            this.crearAndEnviarMS("imgT", trabajador, new String[]{Integer.toString(columna), Integer.toString(cantidadTrabajo)});
            columna += cantidadTrabajo;
        }
    }
}