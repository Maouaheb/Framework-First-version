package plugin;

import interfaces.Interpreter;
import com.google.auto.service.AutoService;
import lib.Importer;
import lib.JavaFileManager;
import org.jdom2.Element;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@AutoService(Interpreter.class)
public class Delta implements Interpreter {
    private String remote=null;

    @Override
    public String getName() {
        return "Delta";
    }
    @Override
    public boolean checConstruct(Element node) {
        try{
            node.getAttribute("name");
        }catch (NullPointerException  e){
            return false;
        }
        List<Boolean> test = new ArrayList<>();
        for (Element type:node.getChildren()) {
            switch (type.getName()){
                case "addFile":
                case "deleteFile":
                    test.add(this.checAddSuppStructure(type));
                    break;
                case "modif":
                    test.add(this.checModifStructure(type));
                    break;
                default:
                    return false;
            }
        }
        return test.size() == node.getChildren().size();
    }
    private boolean checAddSuppStructure(Element adder){
        for (Element file:adder.getChildren()) {
            if(!Objects.equals(file.getName(), "file"))return false;
            try{
                file.getAttribute("path");
            }catch (NullPointerException e){
                return false;
            }
        }
        return true;
    }
    private boolean checModifStructure(Element adder){
        for (Element file:adder.getChildren()) {
            if(!Objects.equals(file.getName(), "file"))return false;
            try{
                file.getAttribute("path");
            }catch (NullPointerException e){
                return false;
            }
            try{
                file.getAttribute("type");
            }catch (NullPointerException e){
                return false;
            }
            if(file.getChildren().size() == 0) return false;
        }
        return true;
    }

    @Override
    public void checImport(String localDirect, Map<String, String> importer, String file) throws IOException {
        List<String> path = List.of(new File(String.valueOf(importer.values().toArray()[0])).getParent()
            .split(Pattern.quote(System.getProperty("file.separator"))));
        String director = String.join("\\",path.subList(0, path.size()-1));
        if(!JavaFileManager.getInstance().isFileInProjectDirectory(file)) {
            if(!this.remote.startsWith("\\") && !file.endsWith("\\")){
                file = "\\".concat(file);
            }
            //TODO:change as in Aspect.java
            JavaFileManager.getInstance().copyFileFrom(director + "\\" + this.remote + file, localDirect + file);
        }
    }

    @Override
    public void construct(Element node, Map<String, String> importer) {
        this.remote = node.getAttributeValue("url");
        if(Objects.equals(this.remote, "") || this.remote == null){
            if(new Importer().isAnUrl(importer.values().toArray()[0].toString())){
                List<String> path = List.of(new File(String.valueOf(importer.values().toArray()[0])).getParent()
                    .split(Pattern.quote(System.getProperty("file.separator"))));
                this.remote = String.join("/",path.subList(0, path.size()-1))+"/tree/"+node.getAttributeValue("name");
            }else{
                this.remote = node.getAttributeValue("name");
            }
        }
        for (Element cat:node.getChildren()) {
            switch (cat.getName()){
                case "addFile":
                    this.fileAdder(cat, importer);
                    break;
                case "modif":
                    this.fileModif(cat);
                    break;
                case "deleteFile":
                    this.fileDeleter(cat);
                    break;
            }
        }
    }

    @Override
    public void setConfigFile(Element node) {
    }

    private void fileDeleter(Element cat) {
        for (Element file:cat.getChildren()) {
            JavaFileManager.getInstance().deleteFile(file.getAttributeValue("path"));
        }
    }

    private void fileModif(Element cat) {
        for (Element addFile:cat.getChildren()) {
            try {
                switch (addFile.getAttributeValue("type")) {
                    case "java":
                        this.javaAdder(addFile);
                        break;
                    case "html":
                        this.htmlAdder(addFile);
                        break;
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void htmlAdder(Element addFile) throws IOException {
        List<String> fileContent = JavaFileManager.getInstance().getFileContentAsLines(addFile.getAttributeValue("path"));
        for (Element add:addFile.getChildren()) {
            Document htmlParse = Jsoup.parse(String.join("\n",fileContent));
            Objects.requireNonNull(htmlParse.select("#" + add.getAttributeValue("endOf")).first()).append(add.getText());
            JavaFileManager.getInstance().saveListInFile(addFile.getAttributeValue("path"), Collections.singletonList(htmlParse.toString()));
        }
    }

    private void javaAdder(Element addFile) throws IOException {
        List<String> fileContent = JavaFileManager.getInstance().getFileContentAsLines(addFile.getAttributeValue("path"));
        //loop from EOF to catch Class end
        /*assomption file end with 1 class end*/
        for (Element add:addFile.getChildren()) {
            for (int i = fileContent.size()-1; i > 0; i--) {
                if(fileContent.get(i).contains("}") && !fileContent.get(i).contains("//")){
                    if(add.getName().equals("add")){
                        fileContent.add(i, add.getText());
                        break;
                    }
                }
            }
        }
        JavaFileManager.getInstance().saveListInFile(addFile.getAttributeValue("path"), fileContent);
    }
    private void fileAdder(Element cat, Map<String, String> importer){
        for (Element file:cat.getChildren()) {
            try {
                this.checImport(new File(importer.keySet().toArray()[0].toString()).getParent(),
                                importer,
                                file.getAttributeValue("path"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
