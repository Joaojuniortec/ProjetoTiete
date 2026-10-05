import java.io.Serializable;
import java.util.Date;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tipo { LOGIN, TEXTO, ARQUIVO, ALERTA, LISTA_USUARIOS, SAIDA }

    private Tipo tipo;
    private String remetente;      // nome do inspetor
    private String industria;      // indústria onde está
    private String destinatario;   // null = todos (broadcast)
    private String texto;
    private String nomeArquivo;
    private byte[] dadosArquivo;
    private Date timestamp;

    public Message(Tipo tipo, String remetente, String destinatario, String texto) {
        this.tipo = tipo;
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.texto = texto;
        this.timestamp = new Date();
    }

    public Tipo getTipo() { return tipo; }
    public String getRemetente() { return remetente; }
    public String getDestinatario() { return destinatario; }
    public String getTexto() { return texto; }
    public Date getTimestamp() { return timestamp; }
    public String getIndustria() { return industria; }
    public void setIndustria(String i) { this.industria = i; }
    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String n) { this.nomeArquivo = n; }
    public byte[] getDadosArquivo() { return dadosArquivo; }
    public void setDadosArquivo(byte[] d) { this.dadosArquivo = d; }
}