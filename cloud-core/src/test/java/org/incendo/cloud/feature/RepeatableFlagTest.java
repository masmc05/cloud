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
package org.incendo.cloud.feature;

import java.util.List;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.TestCommandSender;
import org.incendo.cloud.execution.CommandResult;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;
import static org.incendo.cloud.util.TestUtils.createManager;

class RepeatableFlagTest {

    private CommandManager<TestCommandSender> commandManager;

    @BeforeEach
    void setup() {
        this.commandManager = createManager();
    }

    @Test
    void testParsingRepeatingValueFlags() {
        // Arrange
        this.commandManager.command(
                this.commandManager.commandBuilder("test")
                        .flag(
                                this.commandManager.flagBuilder("flag")
                                        .asRepeatable()
                                        .withComponent(stringParser())
                        )
        );

        // Act
        final CommandResult<TestCommandSender> result = this.commandManager.commandExecutor().executeCommand(
                new TestCommandSender(),
                "test --flag one --flag two --flag three"
        ).join();

        // Assert
        assertThat(result.commandContext().flags().getAll("flag")).containsExactly("one", "two", "three");
    }

    @Test
    void testParsingRepeatingPresenceFlags() {
        // Arrange
        this.commandManager.command(
                this.commandManager.commandBuilder("test")
                        .flag(
                                this.commandManager.flagBuilder("flag")
                                        .withAliases("f")
                                        .asRepeatable()
                        )
        );

        // Act
        final CommandResult<TestCommandSender> result = this.commandManager.commandExecutor().executeCommand(
                new TestCommandSender(),
                "test --flag -fff"
        ).join();

        // Assert
        assertThat(result.commandContext().flags().count("flag")).isEqualTo(4);
    }

    @Test
    void testSuggestingRepeatableFlags() {
        // Arrange
        this.commandManager.command(
                this.commandManager.commandBuilder("test")
                        .flag(
                                this.commandManager.flagBuilder("flag")
                                        .withAliases("f")
                                        .asRepeatable()
                        )
        );

        // Act
        final List<? extends Suggestion> suggestions = this.commandManager.suggestionFactory().suggestImmediately(
                new TestCommandSender(),
                "test --flag --"
        ).list();

        // Assert
        assertThat(suggestions).containsExactly(Suggestion.simple("--flag"));
    }
}
