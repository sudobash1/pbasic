package command;

import java.util.regex.*;
import pbsc.*;
import expression.*;


/**
 * A command to close a device for IO
 */
public class Close extends TrapCommand {

    /**regex to extract and check arguments*/
    private final String m_argumentsReStr = 
        "^" + expressionReStr +
        "(" + m_onReStr + "|" + m_defaultReStr + ")*$";

    /**The expression which gives the device number to close.
     * Must output to trapperRegister.
     */
    protected Expression m_deviceNum;

    /**
     * Create a new Close instance. 
     * @param compiler The main instance of the PbscCompiler.
     * @param line The line the command was found on.
     * @param arguments The arguments to the close command.
     */
    public Close(PbscCompiler compiler, int line, String arguments) {
        super(compiler, line, arguments);

        Matcher m = Pattern.compile(m_argumentsReStr).matcher(arguments);
        if (! m.find()) {
            compiler.error(
                line,
                "Malformed arguments to CLOSE.\n" +
                "Usage: CLOSE <int var> " +
                "{ON <errorNo> GOTO <label>} [DEFAULT GOTO <label>]"
            );
            return;
        }

        m_deviceNum = Expression.create(
            compiler, line, trapperRegister, m.group(1),
            "The device number to CLOSE"
        );
    }

    @Override
    public void generateCode() {

        super.generateCode();
        
        //Generate the code to close the device.
        
        Trapper closeTrapper = new Trapper(
            m_compiler, m_defaultLabel, m_errorMap, m_withinSub
        );

        closeTrapper.addArgument(m_deviceNum);
        closeTrapper.addArgument(
            Expression.create(
                m_compiler, m_line, trapperRegister,
                ""+m_compiler.SYSCALL_CLOSE, "The CLOSE syscall number"
            )
        );

        closeTrapper.generateCode();
    }

    @Override
    public int stackReq() {
        return 2;
    }
}

