import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ServerTCP {

    private static final int PORTA = 5000;
    // Mapa de clientes conectados: nome -> handler
    private static final Map<String, ClientHandler> clientes = new ConcurrentHashMap<>();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static PrintWriter logFile;

    public static void main(String[] args) throws IOException {
        new File("arquivos_recebidos").mkdirs();
        logFile = new PrintWriter(new FileWriter("log_secretaria.txt", true));

        ServerSocket serverSocket = new ServerSocket(PORTA);
        log("Servidor da Secretaria iniciado na porta " + PORTA);
        System.out.println("Aguardando conexões dos inspetores...");

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            new Thread(handler).start();
        }
    }

    static synchronized void log(String msg) {
        String linha = "[" + SDF.format(new Date()) + "] " + msg;
        System.out.println(linha);
        logFile.println(linha);
        logFile.flush();
    }

    static void broadcast(Message m, String remetenteExcluir) {
        for (Map.Entry<String, ClientHandler> entry : clientes.entrySet()) {
            if (!entry.getKey().equals(remetenteExcluir)) {
                entry.getValue().enviar(m);
            }
        }
    }

    static void enviarPrivado(Message m) {
        ClientHandler destino = clientes.get(m.getDestinatario());
        if (destino != null) {
            destino.enviar(m);
        } else {
            log("Destinatário não encontrado: " + m.getDestinatario());
        }
    }

    static void atualizarListaUsuarios() {
        String nomes = String.join(",", clientes.keySet());
        Message lista = new Message(Message.Tipo.LISTA_USUARIOS, "SERVIDOR", null, nomes);
        for (ClientHandler h : clientes.values()) h.enviar(lista);
    }

    // ===== Classe interna que trata cada inspetor conectado =====
    static class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String nome;

        ClientHandler(Socket socket) { this.socket = socket; }

        void enviar(Message m) {
            try {
                out.writeObject(m);
                out.flush();
            } catch (IOException e) {
                log("Erro ao enviar para " + nome + ": " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                Message loginMsg = (Message) in.readObject();
                nome = loginMsg.getRemetente();
                clientes.put(nome, this);
                log("Inspetor conectado: " + nome + " (Indústria: " + loginMsg.getIndustria() + ")");

                broadcast(new Message(Message.Tipo.TEXTO, "SERVIDOR", null,
                        nome + " entrou no sistema."), nome);
                atualizarListaUsuarios();

                Message msg;
                while ((msg = (Message) in.readObject()) != null) {
                    processar(msg);
                }
            } catch (IOException | ClassNotFoundException e) {
                log("Conexão perdida com " + nome);
            } finally {
                if (nome != null) {
                    clientes.remove(nome);
                    broadcast(new Message(Message.Tipo.TEXTO, "SERVIDOR", null,
                            nome + " saiu do sistema."), nome);
                    atualizarListaUsuarios();
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void processar(Message msg) {
            switch (msg.getTipo()) {
                case TEXTO:
                    log(msg.getRemetente() + " -> " +
                        (msg.getDestinatario() == null ? "TODOS" : msg.getDestinatario())
                        + ": " + msg.getTexto());
                    if (msg.getDestinatario() == null) {
                        broadcast(msg, msg.getRemetente());
                    } else {
                        enviarPrivado(msg);
                    }
                    break;

                case ALERTA:
                    log("*** ALERTA *** de " + msg.getRemetente() + ": " + msg.getTexto());
                    broadcast(msg, null); // alerta vai para todos, inclusive remetente (confirmação)
                    break;

                case ARQUIVO:
                    salvarArquivo(msg);
                    if (msg.getDestinatario() == null) {
                        broadcast(msg, msg.getRemetente());
                    } else {
                        enviarPrivado(msg);
                    }
                    break;

                default:
                    break;
            }
        }

        private void salvarArquivo(Message msg) {
            try {
                String caminho = "arquivos_recebidos/" + msg.getRemetente() + "_" + msg.getNomeArquivo();
                try (FileOutputStream fos = new FileOutputStream(caminho)) {
                    fos.write(msg.getDadosArquivo());
                }
                log("Arquivo recebido de " + msg.getRemetente() + ": " + msg.getNomeArquivo());
            } catch (IOException e) {
                log("Erro ao salvar arquivo: " + e.getMessage());
            }
        }
    }
}