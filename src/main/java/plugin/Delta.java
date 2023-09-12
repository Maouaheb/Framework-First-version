package plugin;

import com.google.auto.service.AutoService;
import interfaces.Interpreter;
import lib.Importer;
import lib.JavaFileManager;
import org.jdom2.Element;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public String getxsdDeclaration() throws IOException {
        return Files.readString(Path.of("src/main/resources/delta.xsd.txt"), StandardCharsets.UTF_8);
    }
    // optionnel peut être
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
            if(!this.remote.endsWith("\\") && !file.startsWith("\\")){
                file = "\\".concat(file);
            }
            if(new Importer().isAnUrl(this.remote)){
                JavaFileManager.getInstance().downloadFileFromGitTo(this.remote+file, localDirect+file);
            }else{
                JavaFileManager.getInstance().copyFileFrom(director+"\\"+this.remote+file, localDirect+file);
            }
        }
    }

    @Override
    public void construct(Element node, Map<String, String> importer) {
    	System.out.println("je suis delta");
    	// on donne possibilité s'il y a attribut url dans l'element delta on le récupére
    
        this.remote = node.getAttributeValue("url");
        if(Objects.equals(this.remote, "") || this.remote == null){
            if(new Importer().isAnUrl(importer.values().toArray()[0].toString())){
               // pour avoir les slash pour le split
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
    public void setConfigFile(Element featureModel) {
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
            if(addFile.getChildren().get(0).getText().contains("import ")){
                fileContent.add(1, add.getText());
                break;
            }
            else if(!addFile.getChildren().get(0).getText().contains("{")){
                for (int i = 0; i < fileContent.size() ; i++){
                    if(fileContent.get(i).contains("class")){
                        fileContent.add(i+1, add.getText());
                        break;
                    }
                }
            }
            else{
                for (int i = fileContent.size()-1; i > 0; i--) {
                    if(fileContent.get(i).contains("}") && !fileContent.get(i).contains("//")){
                        if(add.getName().equals("add")){
                            fileContent.add(i, add.getText());
                            break;
                        }
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
