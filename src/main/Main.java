package main;

import errors.ErrorManager;
import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;
import parser.Parser;
import util.SourceReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static void writeUtf8(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Uso: java Main <ruta_fichero_fuente>");
            return;
        }

        Path input = Paths.get(args[0]);
        Path outDir = Paths.get("out");
        Files.createDirectories(outDir);

        // =========================
        // PASADA 1: SOLO LÉXICO -> tokens.txt
        // =========================
        ErrorManager emLex = new ErrorManager();
        Lexer lexer1 = new Lexer(new SourceReader(input), emLex);

        List<String> tokenLines = new ArrayList<>();
        while (true) {
            Token t = lexer1.nextToken();
            tokenLines.add(t.toString());
            if (t.type == TokenType.EOF) {
                break;
            }
        }
        writeUtf8(outDir.resolve("tokens.txt"), String.join("\n", tokenLines));

        // Si hay errores léxicos, los dejamos en errors.txt y paramos aquí (recomendado)
        if (emLex.hasErrors()) {
            writeUtf8(outDir.resolve("errors.txt"), String.join("\n", emLex.getErrors()));
            System.out.println("Errores léxicos. Generado out/tokens.txt. Ver out/errors.txt");
            return;
        }

        // =========================
        // PASADA 2: PARSER (nuevo lexer)
        // =========================
        ErrorManager emSyn = new ErrorManager();
        Lexer lexer2 = new Lexer(new SourceReader(input), emSyn);

        Parser p = new Parser(lexer2, emSyn);
        p.parseProgram();

        // SIEMPRE generamos entregables
        writeUtf8(outDir.resolve("symbols.txt"), p.getSymbolTable().dump());
        writeUtf8(outDir.resolve("intermediate.txt"), p.getIR().getCode());

        if (emSyn.hasErrors()) {
            writeUtf8(outDir.resolve("errors.txt"), String.join("\n", emSyn.getErrors()));
            System.out.println("Errores detectados. Ver out/errors.txt");
        } else {
            writeUtf8(outDir.resolve("errors.txt"), "");
            System.out.println("OK. (tokens.txt, symbols.txt, intermediate.txt en out/)");
        }

    }
}
