package distributionWork;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import javax.imageio.ImageIO;

/**
 * @auhtor: Carlos Marin Fernandez
 * @auhtor: Victor Font de la Rica
 * @date: 19-may-2015
 * @version: 1.0 
 * @description: La ejecución de cada nodo NoRaiz conlleva un trabajo a realizar, en cada 
 * caso, cada nodo NoRaiz puede provocar un tipo diferente de trabajo para el entorno. Estos
 * tipos de trabajo pueden ser: contador, creación del árbol de expansión o filtrar los 
 * pixeles de una imagen.
 */
public class TrabajoNodo implements Runnable {

    private ArrayBlockingQueue colaMensajes;    
    private NodoTrabajador node;

    public TrabajoNodo(ArrayBlockingQueue queue, NodoTrabajador node) {
        this.colaMensajes = queue;
        this.node = node;
    }
    
    //Dependiendo del trabajo almacenado en la cola, redireccionará al método correspondiente
    @Override
    public void run() {
        boolean trabajosFinalizados = false;
        while (!trabajosFinalizados) {
            try {
                //Seleccionamos un trabajo y miramos el tipo que es
                String[] sms = (String[]) colaMensajes.take();
                if(sms.length == 1) trabajosFinalizados = true;
                else if(sms[1].charAt(sms[1].length()-1) == 'T'){
                    //Según el tipo de mensaje que recibe, actua en consecuencia
                    if(sms[1].startsWith("contador")) trabajoContar(Integer.parseInt(sms[3])); //Cantidad a contar
                    //Posicion Y de la imagen y la cantidad de filas a recorrer
                    else if(sms[1].startsWith("img")) imagenSepia(Integer.parseInt(sms[3]), Integer.parseInt(sms[4]));
                    //Nodo hijo y nodo padre que formaran parte del arbol
                    else this.escribirArbolFichero(sms[0], sms[2]);
                }
                //Si no hay más trabajos a realizar enviamos señales de finalizacion a los nodos padres
                if (this.colaMensajes.isEmpty() && !trabajosFinalizados) {
                    node.setTerminated();
                    node.sendSignal();
                }
            } catch (InterruptedException ex) { System.out.println("ERROR en run de Trabajo.java: " + ex.getMessage()); }
        }
    }

    //Division del trabajo a realizar para el nodo padre y sus hijos
    private void trabajoContar(int trabajoContar) throws InterruptedException {
        //Total a contar por padre y sus hijos, partes proporcionales
        int contarNodo = trabajoContar/(node.getNodosPuedoEnviar().size() + 1);
        contarNodo += trabajoContar%(node.getNodosPuedoEnviar().size() + 1)/2;

        for (int nodoEnviar : node.getNodosPuedoEnviar()) 
            node.crearAndEnviarMS("contadorT", nodoEnviar, new String[]{Integer.toString(contarNodo)});

        int cantidadTotal = 0, cantidadParcial = 0; //Cada decima parte del total, se escribe en el fichero
        int decimaParte = contarNodo/10;
        
        while (cantidadTotal < contarNodo) { //Hasta que no llegamos al total de trabajosContar
            cantidadParcial++;
            cantidadTotal++;
            
            //Si hemos llegado a una de las decimas partes o a la cantidad total
            if(cantidadTotal == contarNodo || cantidadParcial % decimaParte == 0) {
                try {
                    //Leemos
                    node.preSC(node.tokens[0]);
                    FileReader ficheroLeer = new FileReader(EntornoRaiz.ficheroTotales);
                    BufferedReader flujoLeer = new BufferedReader(ficheroLeer);
                    int cantidadTotalFichero = Integer.parseInt(flujoLeer.readLine());
                    flujoLeer.close();
                    ficheroLeer.close();
                    cantidadTotalFichero += cantidadParcial;
                    //Cerramos el fichero y volvemos a abrirlo sobreescribiendo la cantidad
                    FileWriter ficheroEscribir = new FileWriter(EntornoRaiz.ficheroTotales);
                    ficheroEscribir.write(Integer.toString(cantidadTotalFichero));
                    ficheroEscribir.close();
                    node.postSC(node.tokens[0]);
                } catch (IOException ex) { System.out.println("ERROR en trabajoContar de Trabajo.java: " + ex.getMessage()); }
                cantidadParcial = 0;
            }
        }
    }
    
    /**
     * 8 Implementar la parte opcional, difuminación de imágenes, aplicamos un filtrito a la imagen.
     * @param y Altura donde iniciara el proceso
     * @param maxY El total de pixeles a tratar
     * @see Works like Trabajo#countMessage(int)
     */
    private void imagenSepia(int y, int maxY) {
        //Numero de filas de pixeles que tratara cada nodo
        int totalFilas = maxY/(node.getNodosPuedoEnviar().size()+1);
        int alturaPrimer = y+totalFilas;
        int restoCT = maxY%(node.getNodosPuedoEnviar().size()+1);
        for (int i = 0; i < node.getNodosPuedoEnviar().size(); i++) {
            int nodo = node.getNodosPuedoEnviar().get(i);
            if (i == node.getNodosPuedoEnviar().size()-1) totalFilas += restoCT;
            node.crearAndEnviarMS("imgT", nodo, new String[]{Integer.toString(alturaPrimer),Integer.toString(totalFilas)});
            alturaPrimer += totalFilas; //Incrementamos la fila a tratar
            if (i == node.getNodosPuedoEnviar().size()-1) totalFilas -= restoCT;
        }
        
        try {
            //Con la imagen original, creamos una nueva imagen de las mismas dimensiones
            BufferedImage imagenOriginal = ImageIO.read(new File(EntornoRaiz.imagenOriginal));
            BufferedImage imagenModificada = new BufferedImage(imagenOriginal.getWidth(), imagenOriginal.getHeight(), 
                    BufferedImage.TYPE_INT_RGB);
            
            //Recorreremos los pixeles cambiandolos a color sepia.
            for (int i = 0; i < imagenOriginal.getWidth(); i++) {
                for (int j = y; j < y + totalFilas; j++) {
                    Color c = new Color(imagenOriginal.getRGB(i, j));
                    int red = c.getRed();
                    int green = c.getGreen();
                    int blue = c.getBlue();
                    final int gry = (red + green + blue) / 3, sepiaDepth = 20;
                    red = green = blue = gry;
                    red = red + (sepiaDepth * 2);
                    green = green + sepiaDepth;

                    if (red > 255) red = 255;
                    if (green > 255) green = 255;
                    if (blue > 255) blue = 255;

                    //Darken blue color to increase sepia effect
                    blue -= 30;

                    //Normalize if out of bounds
                    if (blue < 0) blue = 0;
                    if (blue > 255) blue = 255;

                    //Lo situamos en las mismas coordenadas.
                    imagenModificada.setRGB(i, j, new Color(red, green, blue).getRGB());
                }
            }
            
            this.node.preSC(node.tokens[1]);
            //Seccion crítica
            BufferedImage modificacion = ImageIO.read(new File(EntornoRaiz.imagenModificada));
            modificacion.setData(imagenModificada.getData(new Rectangle(0, y, imagenOriginal.getWidth(), totalFilas)));
            ImageIO.write(modificacion, "png", new File(EntornoRaiz.imagenModificada));
            this.node.postSC(node.tokens[1]);
        } catch (IOException | InterruptedException ex) { System.out.println("ERROR in imagenSepia de Trabajo.java: " + ex.getMessage()); }
    }
    
    //7. Cada vez que un nodo decide cuál es su padre, almacenar esta información 
    //Escribimos el arbol de expansion en el fichero
    private void escribirArbolFichero(String nodoHijo, String nodoPadre) throws InterruptedException {
        try {
            node.preSC(node.tokens[2]);
            FileWriter writer = new FileWriter(EntornoRaiz.digrafoArbol, true);
            writer.write("\t" + nodoPadre + " -> " + nodoHijo + "\n"); //Mismo formato que en el graph.dot
            writer.close();
            node.postSC(node.tokens[2]);
        } catch (IOException ex) { System.out.println("ERROR en escribirArbolFichero de Trabajo.java: " + ex.getMessage()); }
    }
}