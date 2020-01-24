package org.broadinstitute.ddp;

import java.util.Optional;
import java.util.Scanner;

import com.typesafe.config.Config;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.ConfigManager;

/**
 * A simple shell to interactively play with pex expressions.
 */
public class PexREPL {

    private static final String PROMPT = "> ";

    private static PexInterpreter interp;
    private static Optional<String> userCtx;
    private static Optional<String> instanceCtx;

    /**
     * Executes PEX configurations.
     */
    public static void main(String[] args) {
        Config cfg = ConfigManager.getInstance().getConfig();
        String dbUrl = cfg.getString(ConfigFile.DB_URL);
        int maxConn = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String defaultTimeZoneName = cfg.getString(ConfigFile.DEFAULT_TIMEZONE);
        TransactionWrapper.init(defaultTimeZoneName, new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConn, dbUrl));

        interp = new TreeWalkInterpreter();
        userCtx = Optional.empty();

        printIntro();
        run();
    }

    private static void printIntro() {
        System.out.println("PEX REPL");
        System.out.println("Type `.help` for more information.");
        System.out.println("----------------------------------");
        System.out.flush();
    }

    private static void printHelp() {
        System.out.println("PEX REPL");
        System.out.println();
        System.out.println("An interactive shell for PEX, the Pepper Expression Language.");
        System.out.println();
        System.out.println("Type the expression you want to evaluate and press Enter.");
        System.out.println("A user guid must be provided for evaluation context in order for");
        System.out.println("evaluation to work properly. See commands belows.");
        System.out.println();
        System.out.println("To exit out of this shell, use the commands below.");
        System.out.println("You can also press <Control + C> or <Control + D>.");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("\t.help          Prints this help message.");
        System.out.println("\t.user <arg>    Sets the user context by providing the user guid.");
        System.out.println("\t.instance <arg>     Sets the activity instance context by providing the activity instance guid");
        System.out.println("\t.exit          Exits this interactive shell.");
        System.out.println("\t.quit          Same as `.exit`.");
        System.out.flush();
    }

    private static void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print(PROMPT);
            System.out.flush();

            if (!scanner.hasNextLine()) {
                System.out.println();
                break;
            }
            String line = scanner.nextLine();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith(".exit") || line.startsWith(".quit")) {
                break;
            } else if (line.startsWith(".help")) {
                printHelp();
            } else if (line.startsWith(".user")) {
                String arg = line.replace(".user", "");
                execUserCommand(arg);
            } else if (line.startsWith(".instance")) {
                String arg = line.replace(".instance", "");
                execInstanceCommand(arg);
            } else {
                execExpression(line);
            }
        }
    }

    private static void execUserCommand(String arg) {
        String guid = arg.trim();
        if (guid.isEmpty()) {
            System.out.println("Please provide user guid to set as user context.");
            return;
        }
        userCtx = Optional.of(guid);
    }

    private static void execInstanceCommand(String arg) {
        String guid = arg.trim();
        if (guid.isEmpty()) {
            System.out.println("Please provide the activity instance guid to set as instance context.");
            return;
        }
        instanceCtx = Optional.of(guid);
    }

    private static void execExpression(String expr) {
        if (!userCtx.isPresent()) {
            System.out.println("User context not set, please use the `.user` command.");
            return;
        }

        if (!instanceCtx.isPresent()) {
            System.out.println("Instance context not set, please use the `.instance` command.");
            return;
        }

        try {
            boolean result = TransactionWrapper.withTxn(
                    handle -> interp.eval(expr, handle, userCtx.get(), instanceCtx.get()));
            System.out.println(result);
        } catch (PexException e) {
            System.out.println("Error: " + e);
        }
    }
}
