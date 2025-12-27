package main;

import errors.ErrorManager;
import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;
import util.SourceReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Uso: java Main <ruta_fichero_fuente>");
            return;
        }

        Path input = Paths.get(args[0]);
        Path outDir = Paths.get("out");
        Files.createDirectories(outDir);

        ErrorManager em = new ErrorManager();
        SourceReader sr = new SourceReader(input);
        Lexer lexer = new Lexer(sr, em);

        List<String> tokenLines = new ArrayList<>();

        while (true) {
            Token t = lexer.nextToken();
            tokenLines.add(t.toString());
            if (t.type == TokenType.EOF) break;
        }

        // Java 8-10: no Files.writeString
        Files.write(outDir.resolve("tokens.txt"),
                String.join("\n", tokenLines).getBytes(StandardCharsets.UTF_8));

        if (em.hasErrors()) {
            Files.write(outDir.resolve("errors.txt"),
                    String.join("\n", em.getErrors()).getBytes(StandardCharsets.UTF_8));
            System.out.println("Compilación con errores léxicos. Ver out/errors.txt");
        } else {
            Files.write(outDir.resolve("errors.txt"), new byte[0]);
            System.out.println("Léxico OK. Ver out/tokens.txt");
        }
    }
}
