package command;

import java.util.regex.*;
import java.util.*;
import pbsc.*;
import expression.*;

/**
 * A command to read a number to a device.
 */
public abstract class Reader extends TrapCommand {

    /**Automatically open and close the device as well as read.*/
    protected boolean m_autoOpen = true;

    /**The expression which gives the device number to read from.
     * Must output to trapperRegister.
     */
    protected Expression m_deviceNum;

    /**The expression which gives the device address to read from.
     * Must output to trapperRegister.
     */
    protected Expression m_deviceAddr;

    /**An expression to generate a pointer to the location in memory to read
     * to. Must output to LRegister.
     */
    protected Expression m_pointerExp;

    /**
     * Create a new Reader instance. 
     * @param compiler The main instance of the PbscCompiler.
     * @param line The line the command was found on.
     * @param arguments The string which contains multiple ON statements.
     */
    public Reader(PbscCompiler compiler, int line, String arguments) {
        super(compiler, line, arguments);
    }

    @Override
    public void generateCode() {

        super.generateCode();
        
        if (m_autoOpen) {
            //Generate the code to open the device.
            
            Trapper openTrapper = new Trapper(
                m_compiler, m_defaultLabel, m_errorMap, m_withinSub
            );

            openTrapper.addArgument(m_deviceNum);
            openTrapper.addArgument(
                Expression.create(
                    m_compiler, m_line, trapperRegister,
                    ""+m_compiler.SYSCALL_OPEN, "The OPEN syscall number"
                )
            );

            write("#TRAP to open the device");
            openTrapper.generateCode();
        }

        //Generate the code to read from the device
        Trapper readTrapper = new Trapper(
            m_compiler, m_defaultLabel, m_errorMap, m_withinSub
        );

        readTrapper.addArgument(m_deviceNum);
        readTrapper.addArgument(m_deviceAddr);
        readTrapper.addArgument(
            Expression.create(
                m_compiler, m_line, trapperRegister,
                ""+m_compiler.SYSCALL_READ, "The READ syscall number"
            )
        );

        if (m_autoOpen) {
            write("#TRAP to read from the device");
        }
        readTrapper.generateCode();

        if (m_autoOpen) {
            //Generate the code to close the device

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

            write("#TRAP to open the device");
            closeTrapper.generateCode();
        }

        //Save the read value to variable

        if (m_compiler.debugging()) {
            write("#Save the READ value");
        }

        m_pointerExp.generateCode();
        write("POP", RRegister);
        write("SAVE", RRegister, LRegister);
    }

    @Override
    public int stackReq() {
        return 4;
    }
}
