import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

public class ClientGUI extends JFrame {

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;
    private String nomeInspetor;
    private String industria;

    private JTextArea areaChat;
    private JTextField campoMensagem;
    private JComboBox<String> comboDestinatario;

    public ClientGUI() {
        solicitarLogin();
        montarInterface();
        conectarServidor();
    }

    private void solicitarLogin() {
        nomeInspetor = JOptionPane.showInputDialog("Nome do inspetor:");
        industria = JOptionPane.showInputDialog("Indústria/local de inspeção:");
        if (nomeInspetor == null || nomeInspetor.isBlank()) System.exit(0);
    }

    private void montarInterface() {
        setTitle("Monitoramento Rio Tietê - Inspetor: " + nomeInspetor);
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        areaChat = new JTextArea();
        areaChat.setEditable(false);
        add(new JScrollPane(areaChat), BorderLayout.CENTER);

        JPanel painelInferior = new JPanel(new BorderLayout());

        comboDestinatario = new JComboBox<>();
        comboDestinatario.addItem("TODOS");
        painelInferior.add(comboDestinatario, BorderLayout.NORTH);

        campoMensagem = new JTextField();
        campoMensagem.addActionListener(e -> enviarTexto());
        painelInferior.add(campoMensagem, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel();
        JButton btnEnviar = new JButton("Enviar");
        btnEnviar.addActionListener(e -> enviarTexto());

        JButton btnArquivo = new JButton("Enviar Arquivo");
        btnArquivo.addActionListener(e -> enviarArquivo());

        JButton btnAlerta = new JButton("ALERTA!");
        btnAlerta.setBackground(Color.RED);
        btnAlerta.setForeground(Color.WHITE);
        btnAlerta.addActionListener(e -> enviarAlerta());

        JButton btnEmoji1 = new JButton(":)");
        btnEmoji1.addActionListener(e -> campoMensagem.setText(campoMensagem.getText() + " :) "));
        JButton btnEmoji2 = new JButton(":(");
        btnEmoji2.addActionListener(e -> campoMensagem.setText(campoMensagem.getText() + " :( "));
        JButton btnEmoji3 = new JButton("⚠");
        btnEmoji3.addActionListener(e -> campoMensagem.setText(campoMensagem.getText() + " ⚠ "));

        painelBotoes.add(btnEmoji1);
        painelBotoes.add(btnEmoji2);
        painelBotoes.add(btnEmoji3);
        painelBotoes.add(btnEnviar);
        painelBotoes.add(btnArquivo);
        painelBotoes.add(btnAlerta);

        painelInferior.add(painelBotoes, BorderLayout.SOUTH);
        add(painelInferior, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void conectarServidor() {
        try {
            socket = new Socket("localhost", 5000); // trocar pelo IP da Secretaria
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Message login = new Message(Message.Tipo.LOGIN, nomeInspetor, null, "login");
            login.setIndustria(industria);
            out.writeObject(login);
            out.flush();

            new Thread(this::receberMensagens).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erro ao conectar ao servidor: " + e.getMessage());
            System.exit(1);
        }
    }

    private void receberMensagens() {
        try {
            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                tratarMensagemRecebida(msg);
            }
        } catch (Exception e) {
            areaChat.append("Conexão com o servidor encerrada.\n");
        }
    }

    private void tratarMensagemRecebida(Message msg) {
        switch (msg.getTipo()) {
            case TEXTO:
                areaChat.append(msg.getRemetente() + ": " + msg.getTexto() + "\n");
                break;
            case ALERTA:
                areaChat.append("*** ALERTA de " + msg.getRemetente() + ": " + msg.getTexto() + " ***\n");
                break;
            case ARQUIVO:
                try {
                    Files.write(Paths.get("recebido_" + msg.getNomeArquivo()), msg.getDadosArquivo());
                    areaChat.append("[Arquivo recebido de " + msg.getRemetente() + ": " + msg.getNomeArquivo() + "]\n");
                } catch (IOException e) {
                    areaChat.append("Erro ao salvar arquivo recebido.\n");
                }
                break;
            case LISTA_USUARIOS:
                SwingUtilities.invokeLater(() -> {
                    comboDestinatario.removeAllItems();
                    comboDestinatario.addItem("TODOS");
                    for (String u : msg.getTexto().split(",")) {
                        if (!u.equals(nomeInspetor) && !u.isBlank()) comboDestinatario.addItem(u);
                    }
                });
                break;
            default:
                break;
        }
    }

    private void enviarTexto() {
        String texto = campoMensagem.getText().trim();
        if (texto.isEmpty()) return;
        String destino = (String) comboDestinatario.getSelectedItem();
        destino = "TODOS".equals(destino) ? null : destino;

        Message m = new Message(Message.Tipo.TEXTO, nomeInspetor, destino, texto);
        m.setIndustria(industria);
        enviar(m);
        areaChat.append("Eu: " + texto + "\n");
        campoMensagem.setText("");
    }

    private void enviarAlerta() {
        String texto = JOptionPane.showInputDialog("Descreva o alerta (ex: vazamento detectado):");
        if (texto == null || texto.isBlank()) return;
        Message m = new Message(Message.Tipo.ALERTA, nomeInspetor, null, texto);
        m.setIndustria(industria);
        enviar(m);
    }

    private void enviarArquivo() {
        JFileChooser chooser = new JFileChooser();
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File arquivo = chooser.getSelectedFile();
            try {
                byte[] dados = Files.readAllBytes(arquivo.toPath());
                String destino = (String) comboDestinatario.getSelectedItem();
                destino = "TODOS".equals(destino) ? null : destino;

                Message m = new Message(Message.Tipo.ARQUIVO, nomeInspetor, destino, "Arquivo enviado");
                m.setNomeArquivo(arquivo.getName());
                m.setDadosArquivo(dados);
                enviar(m);
                areaChat.append("[Você enviou o arquivo: " + arquivo.getName() + "]\n");
            } catch (IOException e) {
                areaChat.append("Erro ao enviar arquivo.\n");
            }
        }
    }

    private void enviar(Message m) {
        try {
            out.writeObject(m);
            out.flush();
        } catch (IOException e) {
            areaChat.append("Erro ao enviar mensagem.\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}