package lib;

import com.profesorfalken.jpowershell.PowerShell;
import com.profesorfalken.jpowershell.PowerShellNotAvailableException;
import com.profesorfalken.jpowershell.PowerShellResponse;

public class CompileManager {
    private static CompileManager instance = null;
    public static CompileManager getInstance(){
        if (instance == null){
            instance = new CompileManager();
        }
        return instance;
    }

    /**
     * launch maven compilation from powershell
     * @param path String path to pom.xml file
     * @param command String command maven to execute
     * @throws PowerShellNotAvailableException powershell error
     */
    public void maven_powerShell(String path, String command) throws PowerShellNotAvailableException {
        PowerShell powerShell = PowerShell.openSession();
        PowerShellResponse response = powerShell.executeCommand("cd "+path);
        response = powerShell.executeCommand("mvn "+command);
        powerShell.isLastCommandInError();
        powerShell.close();
        System.out.println(response.getCommandOutput());
        if(response.isTimeout() || response.isError()){
            System.out.println("erreur Maven");
        }
    }
}
