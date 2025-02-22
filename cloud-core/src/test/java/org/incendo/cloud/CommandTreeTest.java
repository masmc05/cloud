//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package org.incendo.cloud;

import io.leangen.geantyref.TypeToken;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.AmbiguousNodeException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.google.common.truth.Truth.assertThat;
import static org.incendo.cloud.parser.standard.EnumParser.enumParser;
import static org.incendo.cloud.parser.standard.FloatParser.floatParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;
import static org.incendo.cloud.util.TestUtils.createManager;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CommandTree} integration tests.
 */
@SuppressWarnings("unchecked")
class CommandTreeTest {

    private CommandManager<TestCommandSender> commandManager;

    @BeforeEach
    void setup() {
        this.commandManager = createManager();
    }

    @Test
    void testMultiLiteralParsing() {
        // Arrange
        final int defaultInputNumber = ThreadLocalRandom.current().nextInt();
        this.commandManager.command(
                this.commandManager.commandBuilder("test", CommandMeta.empty())
                        .literal("one")
                        .build()
        ).command(
                this.commandManager.commandBuilder("test", CommandMeta.empty())
                        .literal("two")
                        .permission("no")
                        .build()
        ).command(
                this.commandManager.commandBuilder("test", CommandMeta.empty())
                        .literal("opt")
                        .optional("num", integerParser(), DefaultValue.constant(defaultInputNumber))
                        .build()
        );

        final Executor executor = ExecutionCoordinator.nonSchedulingExecutor();

        // Act
        final CompletableFuture<Command<TestCommandSender>> command1 = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("test one"),
                executor
        );
        final CompletableFuture<Command<TestCommandSender>> command2 = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("test two"),
                executor
        );
        final CompletableFuture<Command<TestCommandSender>> command3 = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("test opt"),
                executor
        );
        final CompletableFuture<Command<TestCommandSender>> command4 = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("test opt 12"),
                executor
        );

        // Assert
        assertThat(command1.join()).isNotNull();
        assertThat(assertThrows(CompletionException.class, command2::join)).hasCauseThat()
                .isInstanceOf(NoPermissionException.class);
        assertThat(command3.join().toString()).isEqualTo("test opt num");
        assertThat(command4.join().toString()).isEqualTo("test opt num");
    }

    @Test
    void testAliasedRouting() {
        // Arrange
        final int defaultInputNumber = ThreadLocalRandom.current().nextInt();
        final Command<TestCommandSender> command = this.commandManager.commandBuilder(
                        "test", Collections.singleton("other"), CommandMeta.empty()
                ).literal("opt", "öpt")
                .optional("num", integerParser(), DefaultValue.constant(defaultInputNumber))
                .build();
        this.commandManager.command(command);

        // Act
        final Command<TestCommandSender> result = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("other öpt 12"),
                ExecutionCoordinator.nonSchedulingExecutor()
        ).join();

        // Assert
        assertThat(result).isEqualTo(command);
    }

    @Test
    void getSuggestions() {
        // Arrange
        this.commandManager.command(
                this.commandManager.commandBuilder("test")
                        .literal("a")
        );
        this.commandManager.command(
                this.commandManager.commandBuilder("test")
                        .literal("b")
        );

        // Act
        final List<? extends Suggestion> results = this.commandManager.commandTree().getSuggestions(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("test "),
                ExecutionCoordinator.nonSchedulingExecutor()
        ).join().list();

        // Assert
        assertThat(results).containsExactly(Suggestion.simple("a"), Suggestion.simple("b"));
    }

    @Test
    void testDefaultParser() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = mock(CommandExecutionHandler.class);
        when(executionHandler.executeFuture(any())).thenReturn(CompletableFuture.completedFuture(null));

        this.commandManager.command(
                this.commandManager.commandBuilder("default")
                        .required(this.commandManager.componentBuilder(Integer.class, "int"))
                        .handler(executionHandler)
                        .build()
        );

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "default 5").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.get(CloudKey.of("int", TypeToken.get(Integer.class)))).isEqualTo(5);
    }

    @Test
    void invalidCommand() {
        assertThrows(CompletionException.class, () -> this.commandManager.commandExecutor().executeCommand(
                new TestCommandSender(),
                "invalid test"
        ).join());
    }

    @Test
    void testProxy() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = mock(CommandExecutionHandler.class);
        when(executionHandler.executeFuture(any())).thenReturn(CompletableFuture.completedFuture(null));

        final Command<TestCommandSender> toProxy = this.commandManager.commandBuilder("test")
                .literal("unproxied")
                .required("string", stringParser())
                .required("int", integerParser())
                .literal("anotherliteral")
                .handler(executionHandler)
                .build();
        this.commandManager.command(toProxy);
        this.commandManager.command(this.commandManager.commandBuilder("proxy").proxies(toProxy).build());

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "test unproxied foo 10 anotherliteral").join();
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "proxy foo 10").join();

        // Assert
        verify(executionHandler, times(2)).executeFuture(notNull());
    }

    private CommandExecutionHandler<TestCommandSender> setupFlags() {
        final CommandExecutionHandler<TestCommandSender> executionHandler = mock(CommandExecutionHandler.class);
        when(executionHandler.executeFuture(any())).thenReturn(CompletableFuture.completedFuture(null));

        final CommandFlag<Integer> num = this.commandManager.flagBuilder("num")
                .withComponent(integerParser())
                .build();

        this.commandManager.command(this.commandManager.commandBuilder("flags")
                .flag(this.commandManager.flagBuilder("test")
                        .withAliases("t")
                        .build())
                .flag(this.commandManager.flagBuilder("test2")
                        .withAliases("f")
                        .build())
                .flag(num)
                .flag(this.commandManager.flagBuilder("enum").withComponent(enumParser(FlagEnum.class)))
                .handler(executionHandler)
                .build());

        return executionHandler;
    }

    @Test
    void testFlags_NoFlags() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().contains("test")).isFalse();
    }

    @Test
    void testFlags_PresenceFlag() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags --test").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().contains("test")).isTrue();
    }

    @Test
    void testFlags_PresenceFlagShortForm() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags -t").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().contains("test")).isTrue();
    }

    @Test
    void testFlags_NonexistentFlag() {
        // Arrange
        this.setupFlags();

        // Act & Assert
        assertThrows(
                CompletionException.class, () ->
                        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags --test --nonexistent").join()
        );
    }

    @Test
    void testFlags_MultiplePresenceFlags() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags --test --test2").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().contains("test")).isTrue();
        assertThat(context.flags().contains("test2")).isTrue();
    }

    @Test
    void testFlags_NonPrefixedPresenceFlag() {
        // Arrange
        this.setupFlags();

        // Act
        assertThrows(
                CompletionException.class, () ->
                        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags --test test2").join()
        );
    }

    @Test
    void testFlags_ValueFlag() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags --num 500").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().<Integer>getValue("num")).hasValue(500);
    }

    @Test
    void testFlags_MultipleValueFlagsFollowedByPresence() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags --num 63 --enum potato --test").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().<Integer>getValue("num")).hasValue(63);
        assertThat(context.flags().<FlagEnum>getValue("enum")).hasValue(FlagEnum.POTATO);
    }

    @Test
    void testFlags_ShortFormPresenceFlagsFollowedByMultipleValueFlags() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = this.setupFlags();

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "flags -tf --num 63 --enum potato").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.flags().contains("test")).isTrue();
        assertThat(context.flags().contains("test2")).isTrue();
        assertThat(context.flags().<Integer>getValue("num")).hasValue(63);
        assertThat(context.flags().<FlagEnum>getValue("enum")).hasValue(FlagEnum.POTATO);
    }

    @Test
    void testAmbiguousNodes() {
        // Call setup(); after each time we leave the Tree in an invalid state
        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .required("string", stringParser())
        );
        assertThrows(AmbiguousNodeException.class, () ->
                this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                        .required("integer", integerParser())));
        this.setup();

        // Literal and argument can co-exist, not ambiguous
        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .required("string", stringParser())
        );
        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .literal("literal"));
        this.setup();

        // Two literals (different names) and argument can co-exist, not ambiguous
        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .literal("literal"));
        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .literal("literal2"));

        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .required("integer", integerParser()));
        this.setup();

        // Two literals with the same name can not co-exist, causes 'duplicate command chains' error
        this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                .literal("literal"));
        assertThrows(IllegalStateException.class, () ->
                this.commandManager.command(this.commandManager.commandBuilder("ambiguous")
                        .literal("literal")));
        this.setup();
    }

    @Test
    void testLiteralRepeatingArgument() {
        // Build a command with a literal repeating
        Command<TestCommandSender> command = this.commandManager.commandBuilder("repeatingargscommand")
                .literal("repeat")
                .literal("middle")
                .literal("repeat")
                .build();

        // Verify built command has the repeat argument twice
        List<CommandComponent<TestCommandSender>> args = command.components();
        assertThat(args.size()).isEqualTo(4);
        assertThat(args.get(0).name()).isEqualTo("repeatingargscommand");
        assertThat(args.get(1).name()).isEqualTo("repeat");
        assertThat(args.get(2).name()).isEqualTo("middle");
        assertThat(args.get(3).name()).isEqualTo("repeat");

        // Register
        this.commandManager.command(command);

        // If internally it drops repeating arguments, then it would register:
        // > /repeatingargscommand repeat middle
        // So check that we can register that exact command without an ambiguity exception
        this.commandManager.command(
                this.commandManager.commandBuilder("repeatingargscommand")
                        .literal("repeat")
                        .literal("middle")
        );
    }

    @Test
    void testAmbiguousLiteralOverridingArgument() {
        /* Build two commands for testing literals overriding variable arguments */
        this.commandManager.command(
                this.commandManager.commandBuilder("literalwithvariable")
                        .required("variable", stringParser())
        );

        this.commandManager.command(
                this.commandManager.commandBuilder("literalwithvariable")
                        .literal("literal", "literalalias")
        );

        final Executor executor = ExecutionCoordinator.nonSchedulingExecutor();

        /* Try parsing as a variable, which should match the variable command */
        final Command<TestCommandSender> variableResult = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("literalwithvariable argthatdoesnotmatch"),
                executor
        ).join();
        assertThat(variableResult).isNotNull();
        assertThat(variableResult.rootComponent().name()).isEqualTo("literalwithvariable");
        assertThat(variableResult.components().get(1).name()).isEqualTo("variable");

        /* Try parsing with the main name literal, which should match the literal command */
        final Command<TestCommandSender> literalResult = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("literalwithvariable literal"),
                executor
        ).join();
        assertThat(literalResult).isNotNull();
        assertThat(literalResult.rootComponent().name()).isEqualTo("literalwithvariable");
        assertThat(literalResult.components().get(1).name()).isEqualTo("literal");

        /* Try parsing with the alias of the literal, which should match the literal command */
        final Command<TestCommandSender> literalAliasResult = this.commandManager.commandTree().parse(
                new CommandContext<>(new TestCommandSender(), this.commandManager),
                CommandInput.of("literalwithvariable literalalias"),
                executor
        ).join();
        assertThat(literalAliasResult).isNotNull();
        assertThat(literalAliasResult.rootComponent().name()).isEqualTo("literalwithvariable");
        assertThat(literalAliasResult.components().get(1).name()).isEqualTo("literal");
    }

    @Test
    void testDuplicateArgument() {
        // Arrange
        final CommandComponent<TestCommandSender> component =
                StringParser.<TestCommandSender>stringComponent(StringParser.StringMode.SINGLE).name("test").build();
        this.commandManager.command(this.commandManager.commandBuilder("one").argument(component));

        // Act & Assert
        this.commandManager.command(this.commandManager.commandBuilder("two").argument(component));
    }

    @Test
    void testFloats() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = mock(CommandExecutionHandler.class);
        when(executionHandler.executeFuture(any())).thenReturn(CompletableFuture.completedFuture(null));

        this.commandManager.command(
                this.commandManager.commandBuilder("float")
                        .required("num", floatParser())
                        .handler(executionHandler)
                        .build()
        );

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "float 0.0").join();
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "float 100").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler, times(2)).executeFuture(contextArgumentCaptor.capture());

        final Stream<Float> values = contextArgumentCaptor.getAllValues()
                .stream()
                .map(context -> context.<Float>get("num"));
        assertThat(values).containsExactly(0.0f, 100f);
    }

    @Test
    void testOptionals() {
        // Arrange
        final CommandExecutionHandler<TestCommandSender> executionHandler = mock(CommandExecutionHandler.class);
        when(executionHandler.executeFuture(any())).thenReturn(CompletableFuture.completedFuture(null));

        this.commandManager.command(
                this.commandManager.commandBuilder("optionals")
                        .optional("opt1", stringParser())
                        .optional("opt2", stringParser())
                        .handler(executionHandler)
                        .build()
        );

        // Act
        this.commandManager.commandExecutor().executeCommand(new TestCommandSender(), "optionals").join();

        // Assert
        final ArgumentCaptor<CommandContext<TestCommandSender>> contextArgumentCaptor = ArgumentCaptor.forClass(
                CommandContext.class
        );
        verify(executionHandler).executeFuture(contextArgumentCaptor.capture());

        final CommandContext<TestCommandSender> context = contextArgumentCaptor.getValue();
        assertThat(context.getOrDefault(CloudKey.of("opt1", TypeToken.get(String.class)), null)).isNull();
        assertThat(context.getOrDefault(CloudKey.of("opt2", TypeToken.get(String.class)), null)).isNull();
    }

    enum FlagEnum {
        POTATO,
        CARROT,
        ONION,
        PROXI
    }
}
