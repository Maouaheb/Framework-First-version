package plugin;

import interfaces.Interpreter;
import com.google.auto.service.AutoService;
import org.jdom2.Attribute;
import org.jdom2.Element;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


@AutoService(Interpreter.class)
public class SpringPreprocessor implements Interpreter {
    private String file =null;
    private final String detector="##";
    private final Map<String, String> configVar=new HashMap<>();
    @Override
    public String getName() {
        return "SpringPreprocessor";
    }
    @Override
    public String getxsdDeclaration() throws IOException {
        return Files.readString(Path.of("src/main/resources/SpringPreprocessor.xsd.txt"), StandardCharsets.UTF_8);
    }
    @Override
    public void checImport(String localDirect, Map<String, String> importer, String file) {

    }
    @Override
    public void construct(Element node, Map<String, String> importer) {
        for (Element file:node.getChildren()) {
            try {
                if(lib.JavaFileManager.getInstance().isFileInProjectDirectory(file.getAttributeValue("path"))){
                    List<String> lines = lib.JavaFileManager.getInstance().getFileContentAsLines(file.getAttributeValue("path"));
                    List<Element> varList = file.getChildren("var");
                    Map<String, String> fileVar = new HashMap<>();
                    for (Element variable:varList) {
                        variable.getAttributes().forEach(a->fileVar.put(a.getName(), a.getValue()));
                    }
                    lines = this.defineHandler(lines);
                    int lineStart;
                    while(true) {
                        int lineIf=0;
                        int lineElse=-1;
                        do{
                            if(fileVar.keySet().stream().noneMatch(lines.get(lineIf)::contains))lineStart=lineIf+1;
                            else {
                                lineStart = 0;
                            }
                            lineIf = this.lineNbrNextPreprocessorInList(lines, "if", lineStart);
                            if(lineIf==-1) break;
                        }while(!(lineIf >-1) || fileVar.keySet().stream().noneMatch(lines.get(lineIf)::contains));
                        if(lineIf<0){
                            break;
                        }
                        lineElse = this.lineNbrNextPreprocessorInList(lines, "else", lineIf);
                        int lineEndIf = this.lineNbrNextPreprocessorInList(lines, "endif", lineIf);
                        this.directiveHandler(lineIf, lineElse, lineEndIf, file, lines);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private List<String> defineHandler(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if(lines.get(i).contains(this.detector) && lines.get(i).contains("define")){
                String var = lines.get(i);
                var = var.replace(this.detector,"");
                var = var.replace("define","");
                if(this.configVar.keySet().stream().anyMatch(var::contains)){
                    String old = this.configVar.keySet().stream().filter(var::contains).collect(Collectors.toList()).get(0);
                    String replace = this.configVar.get(old);
                    var.trim().split("\\s+");
                    var = var.trim().split("\\s+")[0]+" "+replace;
                }
                int cpt = 0;
                for (String line: lines) {
                    lines.set(cpt, line.replace(var.trim().split("\\s+")[0], var.trim().substring(var.trim().indexOf(' ')+1)));
                    cpt++;
                }
                lines.remove(i);
            }
        }
        return lines;
    }

    @Override
    public void setConfigFile(Element node){
        for (Element var:node.getChildren()) {
            List<Attribute> attr= var.getAttributes();
            for (Attribute a:attr) {
                this.configVar.put(a.getName(), a.getValue());
            }
        }
    }

    private void directiveHandler(int lineIf, int lineElse, int lineEndIf, Element file, List<String>lines) throws ScriptException, IOException {
        //ELSE statement found in file
        if (lineElse != -1 && lineElse < lineEndIf) {
            //IF statement is true AND ELSE statement founded
            if (this.IsIfStatementTrue(lines.get(lineIf))) {
                lines.remove(lineIf);//remove if line
                lines = this.removeLinesInListFromTo(lineIf, "else", "endif", lines);//remove else statement
                lib.JavaFileManager.getInstance().saveListInFile(file.getAttributeValue("path"), lines);
            }
            //IF statement is false AND ELSE statement founded
            else {
                lines = (this.removeLinesInListFromTo(lineIf, "if", "else", lines));//remove if statement
                lineEndIf = this.lineNbrNextPreprocessorInList(lines, "endif", lineIf);
                lines.remove(lineEndIf);//remove endif line
                lib.JavaFileManager.getInstance().saveListInFile(file.getAttributeValue("path"), lines);
            }
        }
        //no ELSE statement found in file
        else {
            //IF statement is true AND ELSE not founded
            if (this.IsIfStatementTrue(lines.get(lineIf))) {
                lines.remove(lineIf);//remove if line
                lineEndIf = this.lineNbrNextPreprocessorInList(lines, "endif", lineIf);
                lines.remove(lineEndIf);//remove endif line
                lib.JavaFileManager.getInstance().saveListInFile(file.getAttributeValue("path"), lines);
            }
            //IF statement is false AND ELSE not founded
            else {
                lines = (this.removeLinesInListFromTo(lineIf, "if", "endif", lines));//remove if statement
                lib.JavaFileManager.getInstance().saveListInFile(file.getAttributeValue("path"), lines);
            }
        }
    }
    private int lineNbrNextPreprocessorInList(List<String>lines, String construct, int start) {
        for (int i = start; i < lines.size(); i++) {
            if(lines.get(i).contains(this.detector) && lines.get(i).contains(construct)){
                return i;
            }
        }
        return -1;
    }
    private boolean IsIfStatementTrue(String line) throws ScriptException {
        for (Map.Entry<String, String> variable:this.configVar.entrySet()) {
            line = line.replace(variable.getKey(), "\""+variable.getValue()+"\"");
        }
        line = line.replace("##", "");
        line = line.replace("if", "");
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
        Object result = engine.eval(line);
        return (Boolean) result;
    }
    private List<String> removeLinesInListFromTo(int start, String detector1, String detector2, List<String> lines){
        int lineDetector1 = this.lineNbrNextPreprocessorInList(lines, detector1, start);
        int lineDetector2 = this.lineNbrNextPreprocessorInList(lines, detector2, start);
        //remove else to endif statement
        for (int i = lineDetector1; i <= lineDetector2; i++) {
            if(lines.get(i).contains(detector2) && lines.get(i).contains(this.detector)){
                lines.remove(i);
                break;
            }else{
                lines.remove(i);
                i--;
            }
        }
        return lines;
    }
}















