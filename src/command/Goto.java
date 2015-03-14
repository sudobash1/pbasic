package command;

import java.util.*;
import pbsc.*;
import expression.*;

/**
 * Jump to a label.
 */
public class Goto extends Command {

    /**The label to jump to.*/
    private final String m_label;

    /**The scope this was declared in.*/
    private final ArrayList<String> m_namespaceStack;

    /**
     * jump to a label
     * @param compiler The main instance of the PbscCompiler.
     * @param line The line the command was found on.
     * @param label The label (without scope mangling) to jump to.
     */
    public Goto(PbscCompiler compiler, int line, String label) {
        super(compiler, line);

        label = label.trim();
        m_namespaceStack = compiler.copyNamespace();

        if (! label.matches(idReStr)) {
            m_compiler.error(
                line, "Invalid label name `" + label + "'"
            );
            m_label = null;
            return;
        }

        m_label = label;
    }

    @Override
    public void checkLabels() {
        if (m_label != null) {
            Label.checkExists(m_compiler, m_line, m_label);
        }
    }

    @Override
    public String generateCode() {
        return super.generateCode() +
               "BRANCH " + m_label + endl();
    }

    @Override
    public int stackReq() {
        return 0; //XXX
    }
}
