package pbsc;

import java.io.*;
import java.lang.*;
import java.util.*;
import java.util.regex.*;
import command.*;

public class PbscCompiler {

    /**These are the methods in the Run Time Library.*/
    public enum RunTimeLibrary {
        CLEAR_STACK
    };

    /**These words may not be used as labels or variables.*/
    private static final String[] reservedWords = {
        "and", "break", "case", "define", "default", "done", "else", "end",
        "fi", "for", "gosub", "goto", "if", "int", "lable", "list", "mod",
        "on", "or", "p", "print", "printl", "read", "readl", "rem", "set",
        "setl", "sub", "subr", "to", "while", "write"
    };

    /**Constant to exit the current program */
    public static final String SYSCALL_EXIT = "0";
    /**Constant to output a number*/
    public static final String SYSCALL_OUTPUT = "1";
    /**Constant to get current process id*/
    public static final String SYSCALL_GETPID = "2";
    /**Constan to access a device*/
    public static final String SYSCALL_OPEN = "3";
    /**Constant to release a device*/
    public static final String SYSCALL_CLOSE = "4";
    /**Constant to get input from device*/
    public static final String SYSCALL_READ = "5";
    /**Constant to send output to device*/
    public static final String SYSCALL_WRITE = "6";
    /**Constant to spawn a new process*/
    public static final String SYSCALL_EXEC = "7";
    /**Constant to yield the CPU to another process*/
    public static final String SYSCALL_YIELD = "8";
    /**Constant to print process state and exit*/
    public static final String SYSCALL_COREDUMP = "9";

    /**Size of each instruction in bytes.*/
    public static final int INSTSIZE = 4;

    /*The below registers are hacks based on the fact that the CPU simulator
     *does not check that the register being accessed is a general register.
     */
    /**The program counter register.*/
    public static final String pcRegister = "R5";
    /**The stack pointer register.*/
    public static final String spRegister = "R6";
    /**The register denoting the bottom of currently accessable RAM.*/
    public static final String baseRegister = "R7";
    /**The register denoting the top of currently accessable RAM.*/
    public static final String limRegister = "R8";

    /**Number of extra instructions OS will add to call syscall exit.*/
    public static final int extraInstructions = 3;

    /**Turns on debugging output*/
    private final boolean m_debug = true;

    /**Turns off debugging for precompiling stage*/
    private boolean m_preCompiling = false;

    /**Maximum number of errors to report before aborting the compilation.*/
    private final int MAX_ERROR = 5;

    /**Number of errors found so far.*/
    private int m_errorCount = 0;
    
    /**Set to true when there has been an error reported.*/
    private boolean m_hasError = false;

    /**
     * A number not found anywhere in the program input.
     * It is used to make labels for compiler use that will not interfere with
     * user made labels.
     */
    private int m_magicNumber;

    /**
     * Maps constants defined in the program to their values.
     * The String is the constant name with scope mangling.
     */
    private Hashtable<String, Integer> m_definesTable = null;

    /**
     * Maps variables to their definition class.
     * The String is the variable name with scope mangling.
     */
    private Hashtable<String, VariableDefinition> m_varTable = null;

    /**
     * Maps run-time library variables to their definition class.
     * The String is the variable name without any magic or mangling.
     */
    private Hashtable<String, VariableDefinition> m_runTimeVarTable = null;


    /**
     * Maps subroutines to their definition class.
     * The String is the subroutine name.
     */
    private Hashtable<String, Sub> m_subTable = null;

    /**
     * Contains all defined labels.
     * The String is the label name with scope mangling.
     */
    private ArrayList<String> m_labels = null;

    /**
     * Every time another scope is entered, the block id gets pushed here.
     * The String is the If/While/For/Sub block id.
     */
    private ArrayList<String> m_namespaceStack = null;

    /**
     * The runTimeLib instance
     */
    private RunTimeLib m_runTimeLib = null;

    /** The StringBuffer for the main body of the program. */
    private StringBuffer m_mainBuffer = null;

    /** The StringBuffer for the header of the program. */
    private StringBuffer m_headerBuffer = null;

    /** If this is true then we are writing to the header, else body. */
    private boolean m_header = false;

    public PbscCompiler() {
        m_varTable = new Hashtable<String, VariableDefinition>();
        m_runTimeLib = new RunTimeLib(this);
        m_runTimeVarTable = new Hashtable<String, VariableDefinition>();
        m_subTable = new Hashtable<String, Sub>();
        m_definesTable = new Hashtable<String,Integer>();
        m_namespaceStack = new ArrayList<String>();
        m_labels  = new ArrayList<String>();
        m_mainBuffer = new StringBuffer();
        m_headerBuffer = new StringBuffer();
    }

    /**
     * Returns if debugging is on.
     * @return true if debugging is enabled.
     */
    public boolean debugging() {
        return m_debug && !m_preCompiling;
    }

    /**
     * Prints an error message to the screen and flags the compilation
     * as failed.
     * @param line Line where the error occurred.
     * @param message Error message to display.
     */
    public void error(int line, String message) {
        System.err.print("ERROR: line " + line + ": ");
        System.err.println(message);
        m_hasError = true;
        if (++m_errorCount >= MAX_ERROR) {
            System.err.println(
                "Aborting compilation attempt. " +
                "No more errors will be reported."
            );
            System.err.println("Compilation failed.");
            System.exit(2);
        }
    }

    /**
     * Prints a warning message to the screen but does not flag the compilation
     * as failed.
     * @param line Line where the warning was generated.
     * @param message Warning message to display.
     */
    public void warning(int line, String message) {
        System.err.print("Warning: line " + line + ": ");
        System.err.println(message);
    }

    /**
     * Writes a line to the output file.
     */
    public void write(String s1) {
        StringBuffer sb = m_header ? m_headerBuffer : m_mainBuffer;
        sb.append(s1);
        sb.append(lineEnding());
    }

    /**
     * Writes a line to the output file.
     */
    public void write(String s1, String s2) {
        StringBuffer sb = m_header ? m_headerBuffer : m_mainBuffer;
        sb.append(s1);
        sb.append(" ");
        sb.append(s2);
        sb.append(lineEnding());
    }

    /**
     * Writes a line to the output file.
     */
    public void write(String s1, String s2, String s3) {
        StringBuffer sb = m_header ? m_headerBuffer : m_mainBuffer;
        sb.append(s1);
        sb.append(" ");
        sb.append(s2);
        sb.append(" ");
        sb.append(s3);
        sb.append(lineEnding());
    }

    /**
     * Writes a line to the output file.
     */
    public void write(String s1, String s2, String s3, String s4) {
        StringBuffer sb = m_header ? m_headerBuffer : m_mainBuffer;
        sb.append(s1);
        sb.append(" ");
        sb.append(s2);
        sb.append(" ");
        sb.append(s3);
        sb.append(" ");
        sb.append(s4);
        sb.append(lineEnding());
    }

    /**
     * Writes a line to the output file.
     */
    public void write(
        String s1, String s2, String s3, String s4, String s5
    ) {
        StringBuffer sb = m_header ? m_headerBuffer : m_mainBuffer;
        sb.append(s1);
        sb.append(" ");
        sb.append(s2);
        sb.append(" ");
        sb.append(s3);
        sb.append(" ");
        sb.append(s4);
        sb.append(" ");
        sb.append(s4);
        sb.append(lineEnding());
    }

    /**
     * Returns the line ending used for the generated pidgen file. For now
     * it is just hardcoded to "\n".
     * @return the line ending string.
     */
    public String lineEnding() { return "\n"; };

    /**
     * Return code to call specified runtime library method.
     * @param method The runtime library method call
     */
    public void callRuntimeMethod(PbscCompiler.RunTimeLibrary method) {
        m_runTimeLib.callRuntimeMethod(method);
    }

    /**
     * Return a copy of the namespace stack
     * @return the copy of the namespace stack.
     */
    public ArrayList<String> copyNamespace() {
        return new ArrayList<String>(m_namespaceStack);
    }

    /**
     * Returns if the passed in identifier is a resurved word.
     * @param id the identifier to check.
     * @return true if id is a resurved word.
     */
    public boolean isReservedWord(String id) {
        for (String rid : reservedWords) {
            if (rid.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a variable for the run time library. If no such variable yet exists
     * then create it.
     * @param varName The unique name of the variable. No need for magic.
     * @return The VariableDefinition.
     */
    protected VariableDefinition runTimeLibIntVariable(String varName) {
        if (m_runTimeVarTable.containsKey(varName)) {
            return m_runTimeVarTable.get(varName);
        } else {
            VariableDefinition vd = new IntDefinition(this, -1);
            m_runTimeVarTable.put(varName, vd);
            return vd;
        }
    }

    /**
     * Register a new constant. Checks if constant exists before adding.
     * If the constant already exists, prints out an error.
     * This method is scope aware.
     * @param constName the constant name (without scope mangling).
     * @param value the value of the constant.
     * @param line the line the const is being declaired.
     * @return False if constant already exists.
     */
    public boolean registerNewConstant(
        String constName, Integer value, int line
    ) {
        //Check if constant exists in this scope.
        for (String id : allId(constName)) {
            if (m_definesTable.containsKey(id)) {
                error(line, "Constant `" + constName + "' already exists.");
                return false;
            }
        }
        m_definesTable.put(scopeID(constName), value);
        return true;
    }

    /**
     * Get the value of a constant.
     * If the constant doesn't exist in this scope, prints out an error.
     * This method is scope aware.
     * @param constName the name of the constant
     * @param line the line the const is being referenced from.
     * @return the Integer value of the constant if it exists, else null.
     */
    public Integer getConstantValue(String constName, int line) {
        //Check if constant exists in this scope.
        for (String id : allId(constName)) {
            if (m_definesTable.containsKey(id)) {
                return m_definesTable.get(id);
            }
        }
        error(line, "Undefined constant `" + constName + "'");
        return null;
    }

    /**
     * Register a new varable. Checks if variable exists before adding.
     * If the variables already exists, prints out an error.
     * This method is scope aware.
     * @param varName the variable name (without scope mangling).
     * @param varDefn the VariableDefinition
     * @param line the line the variable is being declaired.
     * @return False if variable already exists.
     */
    public boolean registerNewVariable(
        String varName, VariableDefinition varDefn, int line
    ) {
        //Check if variable exists in this scope.
        for (String id : allId(varName)) {
            if (m_varTable.containsKey(id)) {
                error(
                    line,
                    "Variable `" + varName + "' has already been declared."
                );
                return false;
            }
        }
        m_varTable.put(scopeID(varName), varDefn);
        return true;
    }

    /**
     * Gets the definition class of a variable.
     * If the variable doesn't exist in this scope, prints out an error.
     * This method is scope aware.
     * @param varName the name of the variable
     * @param line the line the variable is being referenced from.
     * @param vital if vital is true, then throw an error if the
     * VariableDefinition does not exist.
     * @return the VariableDefinition if it exists else null.
     */
    public VariableDefinition getVarableDefinition(
        String varName, int line, boolean vital
    ) {
        //Check if variable exists in this scope.
        for (String id : allId(varName)) {
            if (m_varTable.containsKey(id)) {
                return m_varTable.get(id);
            }
        }
        if (vital) { error(line, "Undefined varable `" + varName + "'"); }
        return null;
    }

    /**
     * Register a new subroutine. Checks if subroutine exists before adding.
     * If the subroutine already exists, prints out an error.
     * @param subName the subroutine name.
     * @param sub the Sub instance.
     * @param line the line the subroutine is being declaired.
     * @return False if subroutine with that name already exists.
     */
    public boolean registerNewSub(String subName, Sub sub, int line) {
        //Check if sub with this name exists
        if (m_subTable.containsKey(subName)) {
            error(
                line,
                "A subroutine with the name `" + subName +
                "' has already been declared."
            );
            return false;
        }
        m_subTable.put(subName, sub);
        return true;
    }

    /**
     * Gets the Sub instance of a subroutine.
     * If the subroutine doesn't exist prints out an error.
     * @param subName the name of the subroutine
     * @param line the line the subroutine is being referenced from.
     * @return the VariableDefinition if it exists else null.
     */
    public Sub getSub(String subName, int line) {
        if (m_subTable.containsKey(subName)) {
            return m_subTable.get(subName);
        }
        error(line, "Undefined subroutine `" + subName + "'");
        return null;
    }

    /**
     * Returns the value of the constant or integer literal passed.
     * Handles all error chcking.
     * This method is scope aware.
     * @param constLit the string representation of the constant or literal.
     * @param int the line the constLit is being referenced from.
     * @return the Integer value the constLit represents or null on error.
     */
    public Integer constLit2Integer(String constLit, int line) {
        constLit = constLit.trim().toLowerCase();
        if (constLit.equals("")) {
            error(line, "Integer constant or literal expected");
            return null;
        }

        if (constLit.charAt(0) == '#') {
            //This is a constant
            return getConstantValue(constLit, line);
        } else {
            if (constLit.matches("(-?[1-9][0-9]*|0)")) {
                try {
                    return Integer.valueOf(constLit);
                } catch (NumberFormatException e) {
                    error(
                        line,
                        "Integer literal out of range, `" + constLit + "'."
                    );
                    return null;
                }
            } else {
                error(
                    line,
                    "Integer constant or literal expected. "+
                    "Found `" + constLit + "'"
                );
                return null;
            }
        }
    }

    /**
     * Returns the passed in label with scope mangling applied.
     * @return The scope mangled label.
     */
    public String scopeID(String label) {
        String namespace = "";
        for (String s : m_namespaceStack) {
            namespace += s;
        }
        return namespace + label + "ID" + m_magicNumber;
    }

    /**
     * Apply the magic number to the label to make sure it doesn't interfere
     * with any of the users.
     * @param label The label to apply the magic to.
     * @return The label with magic applied.
     */
    public String applyMagic(String label) {
        return label + "ID" + m_magicNumber; 
    }

    /**
     * Returns a list of all namespace magled ids which the passed in label
     * could represent. Used for determining what to link a label to.
     * This method is scope aware.
     * @param label the label to mangle.
     * @return The ArrayList of all possible ids.
     */
    private ArrayList<String> allId(String label) {
        ArrayList<String> ret = new ArrayList<String>();

        ret.add(label + "ID" + m_magicNumber);

        String namespace = "";
        for (String s : m_namespaceStack) {
            namespace += s;
            ret.add( namespace + label + "ID" + m_magicNumber );
        }

        return ret;
    }

    /**
     * Pushes a id to the scope stack. The id is the id of the if/while/for/sub
     * block just entered.
     * @param scope The scope id.
     */
    public void pushScope(String scope) {
        m_namespaceStack.add(scope);
    }

    /**
     * Pops a id from the scope stack.
     */
    public void popScope() {
        m_namespaceStack.remove(m_namespaceStack.size() - 1);
    }

    /**
     * Find how many newlines there are in the passed string. Used to advance
     * the line counter when going through the file statment by statement.
     * @return The number of newlines counted.
     */
    public static int countNewlines(String s) {
        Matcher m = Pattern.compile("(\n)").matcher(s);
        int lines = 0;
        while (m.find()) { ++lines; }
        return lines;
    }

    /**
     * Find how many instructions there are in the passed string.
     * @return The number of instructions counted
     */
    public static int countInstructions(String s) {
        Matcher m = Pattern.compile(
            "^\\s*[^#:\\s]", Pattern.MULTILINE
        ).matcher(s);
        int lines = 0;
        while (m.find()) { ++lines; }
        return lines;
    }

    /**
     * Reads the file and returns the contents. If there are errors reading the
     * file, then they are printed and System.exit(1) is called.
     *
     * Note: for now '\n' are always used to denote the end of a line.
     *
     * @param path The path to the file to read.
     * @return The contents of the file with \n endings.
     */
    public String readFile(String path) {

        File file = new File(path);
        BufferedReader reader = null;
        StringBuilder sb = new StringBuilder();
        String input;

        try {
            reader = new BufferedReader(new FileReader(file));

            while ((input = reader.readLine()) != null) {
                sb.append(input + '\n');
            }
        } catch (FileNotFoundException e) {
            System.err.println("Cannot find file `" + path + "'.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Cannot open file `" + path + "' for reading.");
            System.exit(1);
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {}
        }

        return sb.toString();
    }

    /**
     * Writes the given string to a file.
     *
     * @param path The path to the file to written to.
     * @param content The content to be placed in the file.
     */
    public void writeFile(String path, String content) {

        PrintWriter fout = null;

        try {
            fout = new PrintWriter(path);
            fout.print(content);
        } catch (FileNotFoundException e) {
            System.err.println("Cannot open file `" + path + "' for writing.");
            System.exit(1);
        } finally {
            if (fout != null) fout.close();
        }
    }

    /**
     * Generates a string containing the addresses variables bound to as
     * Pidgen comments. Used for debugging.
     * @param textSize The size (in bytes) of the .text section of program
     * @param progSize The size (in bytes) of the entire program (vars incl.)
     * @return A pidgen comment
     */
    private String generateSymTabComment(int textSize, int progSize) {

        if (! debugging()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("#MEMORY INFORMATION######################################");
        sb.append(lineEnding());
        sb.append("#");
        sb.append(lineEnding());
        sb.append("# size of command section: ");
        sb.append(textSize);
        sb.append(lineEnding());
        sb.append("# size of entire program: ");
        sb.append(progSize);
        sb.append(lineEnding());
        sb.append("#");
        sb.append(lineEnding());

        sb.append("#VARIABLE TABLE##########################################");
        sb.append(lineEnding());
        sb.append("#");
        sb.append(lineEnding());
        sb.append("# RunTime Library Variables:");
        sb.append(lineEnding());
        for (String key : m_runTimeVarTable.keySet()) {
            sb.append("# ");
            sb.append(key);
            sb.append(" @ ");
            sb.append(""+m_runTimeVarTable.get(key).getAddress());
            sb.append(lineEnding());
        }
        sb.append("#");
        sb.append(lineEnding());
        sb.append("# User Variables:");
        sb.append(lineEnding());
        for (String key : m_varTable.keySet()) {
            sb.append("# ");
            sb.append(key);
            sb.append(" @ ");
            sb.append(""+m_varTable.get(key).getAddress());
            sb.append(lineEnding());
        }
        sb.append("#");
        sb.append(lineEnding());

        sb.append("#CONSTANT TABLE##########################################");
        sb.append(lineEnding());
        sb.append("#");
        sb.append(lineEnding());
        for (String key : m_definesTable.keySet()) {
            sb.append("# ");
            sb.append(key);
            sb.append(" = ");
            sb.append(""+m_definesTable.get(key));
            sb.append(lineEnding());
        }
        sb.append("#");
        sb.append(lineEnding());


        sb.append("#########################################################");
        sb.append(lineEnding());
        sb.append(lineEnding());

        return sb.toString();
    }

    /**
     * Run the compiler with passed in args.
     *
     * The exit codes are:
     *
     * 0 - success
     * 1 - IO error
     * 2 - compile error
     *
     * @return the exit code.
     */
    public int run(String[] args) {
        String input;

        if (args.length < 2) {
            System.err.println("Requires more arguments.");
            System.exit(1);
        }

        input = readFile(args[0]);

        //Generate a magic number not found anywhere in the input.
        do {
            m_magicNumber = (int)(Math.random() * 100000);
        } while ( input.contains(String.valueOf(m_magicNumber)) );

        int line = 1; //What line number are we on?

        //Statements are always delimited by ;
        ArrayList<String> str_commands = new ArrayList<String>(
            Arrays.asList(input.split(";"))
        );
        ArrayList<Command> program = new ArrayList<Command>();

        //Go through the list of statements found in the file and generate
        //the command for each one.
        for(String str_command: str_commands) {
            line += countNewlines(str_command);

            Command newCommand = Command.create(this, line, str_command);

            if (newCommand != null) {
                program.add(newCommand);
            }
        }

        // Now that all labels have been found, make sure that all commands
        // that need labels can link to valid labels.
        for (Command c: program) {
            c.checkLabels();
        }

        if (m_hasError) {
            System.err.println("Compilation failed.");
            System.exit(2);
        }

        /*
         * Front end complete. Now we compile the program to pidgen asm without
         * bound variables. We need to figure out what the size of the program
         * will be before we can bind variables to addresses.
         *
         * This is a bit of a hack and makes it so we must compile like this
         * twice, but it will work for now. It is quite fast enough because
         * compilation is simple.
         */
        m_preCompiling = true;
        m_header = false;
        //StringBuilder pidgenStringBuilder = new StringBuilder();
        for (Command c : program) {
            c.generateCode();
        }
        //pidgenStringBuilder.insert(0, m_runTimeLib.generateCode());
        m_header = true;
        m_runTimeLib.generateCode();

        String pidgenString =
            m_headerBuffer.toString() + m_mainBuffer.toString();

        //Clear the buffers
        m_headerBuffer = new StringBuffer();
        m_mainBuffer = new StringBuffer();

        //The size taken up by program instructions.
        final int programInstSize = countInstructions(pidgenString) * INSTSIZE;

        //Bind the variables to addresses.
        
        /* XXX For now we are assuming that the variables go immediately after
         * the program statements at the top of memory.
         */
        
        int variableLocation = programInstSize + extraInstructions * INSTSIZE;
        for (VariableDefinition vd : m_runTimeVarTable.values()) {
            vd.setAddress(variableLocation);
            variableLocation += vd.getSize();
        }
        for (VariableDefinition vd : m_varTable.values()) {
            vd.setAddress(variableLocation);
            variableLocation += vd.getSize();
        }

        //The last variable is where our staticly allocated memory ends.
        final int endOfStaticMem = variableLocation;

        //Recompile with bound variables
        m_preCompiling = false;
        m_header = false;
        //StringBuilder pidgenStringBuilder = new StringBuilder();
        for (Command c : program) {
            c.generateCode();
        }
        //pidgenStringBuilder.insert(0, m_runTimeLib.generateCode());
        m_header = true;
        m_runTimeLib.generateCode();

        pidgenString = m_headerBuffer.toString() + m_mainBuffer.toString();

        //Generate the symbol table comment string.
        String symTabComment = generateSymTabComment(
            programInstSize, variableLocation - 1
        );

        //Save the output to the output file.
        writeFile(args[1], symTabComment + pidgenString);

        System.out.println("Compilation successful");

        return 0;
    }

    public static void main(String[] args) {
        new PbscCompiler().run(args);
    }

}
