package main;

import errors.ErrorManager;
import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;
import util.SourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Uso: java Main <ruta_fichero_fuente>");
            return;
        }

        Path input = Path.of(args[0]);
        Path outDir = Path.of("out");
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

        Files.writeString(outDir.resolve("tokens.txt"), String.join("\n", tokenLines));

        if (em.hasErrors()) {
            Files.writeString(outDir.resolve("errors.txt"), String.join("\n", em.getErrors()));
            System.out.println("Compilación con errores léxicos. Ver out/errors.txt");
        } else {
            Files.writeString(outDir.resolve("errors.txt"), "");
            System.out.println("Léxico OK. Ver out/tokens.txt");
        }
    }
}
