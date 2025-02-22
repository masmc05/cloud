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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.compound.ArgumentTriplet;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.setting.ManagerSetting;
import org.incendo.cloud.suggestion.FilteringSuggestionProcessor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.type.tuple.Pair;
import org.incendo.cloud.type.tuple.Triplet;
import org.incendo.cloud.util.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.google.common.truth.Truth.assertThat;
import static org.incendo.cloud.parser.standard.ArgumentTestHelper.suggestionList;
import static org.incendo.cloud.parser.standard.BooleanParser.booleanParser;
import static org.incendo.cloud.parser.standard.DurationParser.durationParser;
import static org.incendo.cloud.parser.standard.EnumParser.enumParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerComponent;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringArrayParser.flagYieldingStringArrayParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringComponent;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;
import static org.incendo.cloud.util.TestUtils.createManager;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CommandSuggestionsTest {

    private CommandManager<TestCommandSender> manager;

    @BeforeEach
    void setupManager() {
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("test", "testalias").literal("one").build());
        this.manager.command(manager.commandBuilder("test").literal("two").build());
        this.manager.command(manager.commandBuilder("test")
                .literal("var")
                .required("str", stringParser(), SuggestionProvider.suggestingStrings("one", "two"))
                .required("enum", enumParser(TestEnum.class)));
        this.manager.command(manager.commandBuilder("test")
                .literal("comb")
                .required("str", stringParser(),
                        SuggestionProvider.blocking((c, s) -> suggestionList("one", "two")))
                .optional("num", integerParser(1, 95)));
        this.manager.command(manager.commandBuilder("test")
                .literal("alt")
                .required("num", integerComponent().suggestionProvider(
                        SuggestionProvider.blocking((c, s) -> suggestionList("3", "33", "333")))));

        this.manager.command(manager.commandBuilder("com")
                .requiredArgumentPair("com", Pair.of("x", "y"), Pair.of(Integer.class, TestEnum.class),
                        Description.empty()
                )
                .required("int", integerParser()));

        this.manager.command(manager.commandBuilder("com2")
                .requiredArgumentPair("com", Pair.of("x", "enum"),
                        Pair.of(Integer.class, TestEnum.class), Description.empty()
                ));

        this.manager.command(manager.commandBuilder("flags3")
                .flag(manager.flagBuilder("compound")
                        .withComponent(
                                ArgumentTriplet.of(
                                        manager,
                                        Triplet.of("x", "y", "z"),
                                        Triplet.of(int.class, int.class, int.class)
                                ).simple()
                        )
                )
                .flag(manager.flagBuilder("presence").withAliases("p"))
                .flag(manager.flagBuilder("single").withComponent(integerParser())));

        this.manager.command(manager.commandBuilder("numbers").required("num", integerParser()));
        this.manager.command(manager.commandBuilder("numberswithfollowingargument").required("num", integerParser())
                .required("another_argument", booleanParser()));
        this.manager.command(manager.commandBuilder("numberswithmin")
                .required("num", integerParser(5, 100)));
        this.manager.command(manager.commandBuilder("partial")
                .required(
                        "arg",
                        stringComponent(StringParser.StringMode.SINGLE).suggestionProvider(
                                SuggestionProvider.suggestingStrings("hi", "hey", "heya", "hai", "hello"))
                )
                .literal("literal")
                .build());

        this.manager.command(manager.commandBuilder("literal_with_variable")
                .required(
                        "arg",
                        stringComponent(StringParser.StringMode.SINGLE).suggestionProvider(
                                SuggestionProvider.blocking((ctx, in) ->
                                        suggestionList("veni", "vidi")))
                )
                .literal("now"));
        this.manager.command(manager.commandBuilder("literal_with_variable")
                .literal("vici")
                .literal("later"));

        this.manager.command(manager.commandBuilder("cmd_with_multiple_args")
                .required("number", integerComponent().preprocessor((ctx, input) -> {
                    String argument = input.peekString();
                    if (!argument.equals("1024")) {
                        return ArgumentParseResult.success(true);
                    } else {
                        return ArgumentParseResult.failure(new NullPointerException());
                    }
                }))
                .required("enum", enumParser(TestEnum.class))
                .literal("world"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "test ", "testalias " })
    void Suggestions_ExistingRootAliases_SuggestsLiterals(final @NonNull String input) {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("test", "testalias").literal("one").build());

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        assertThat(suggestions).containsExactly(Suggestion.simple("one"));
    }

    @Test
    void testSimple() {
        final String input = "test";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertTrue(suggestions.isEmpty());
        final String input2 = "test ";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("alt", "comb", "one", "two", "var"), suggestions2);
        final String input3 = "test a";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("alt"), suggestions3);
    }

    @Test
    void testVar() {
        final String input = "test var";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertTrue(suggestions.isEmpty());
        final String input2 = "test var one";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("one"), suggestions2);
        final String input3 = "test var one f";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("foo"), suggestions3);
        final String input4 = "test var one ";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(suggestionList("foo", "bar"), suggestions4);
    }

    @Test
    void testEmpty() {
        final String input = "kenny";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertTrue(suggestions.isEmpty());
    }

    @Test
    void Suggestions_UnknownRootCommand_EmptySuggestions() {
        // Arrange
        final String input = "kenny";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        assertThat(suggestions).isEmpty();
    }

    @Test
    void testComb() {
        final String input = "test comb ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("one", "two"), suggestions);
        final String input2 = "test comb one ";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions2);
        final String input3 = "test comb one 9";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("9", "90", "91", "92", "93", "94", "95"), suggestions3);
    }

    @Test
    void testAltered() {
        final String input = "test alt ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("3", "33", "333"), suggestions);
    }

    @Test
    void testCompound() {
        final String input = "com ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions);
        final String input2 = "com 1 ";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("1 foo", "1 bar"), suggestions2);
        final String input3 = "com 1 foo ";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions3);
        final String input4 = "com2 1 ";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(suggestionList("1 foo", "1 bar"), suggestions4);
    }

    @Test
    void Suggestions_NoFlagsEnteredAfterVariable_SuggestsFlags() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .required("num", IntegerParser.integerParser())
                .flag(manager.flagBuilder("enum")
                        .withComponent(enumParser(TestEnum.class))
                        .build())
                .flag(manager.flagBuilder("static")
                        .build())
                .build());
        final String input = "flags 10 ";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("--enum", "--static"), suggestions);
    }

    @Test
    void Suggestions_EnumFlagEntered_SuggestsFlagValues() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .required("num", IntegerParser.integerParser())
                .flag(manager.flagBuilder("enum")
                        .withComponent(enumParser(TestEnum.class))
                        .build())
                .flag(manager.flagBuilder("static")
                        .build())
                .build());
        final String input = "flags 10 --enum ";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("foo", "bar"), suggestions);
    }

    @Test
    void Suggestions_FlagValueEntered_SuggestsOtherFlag() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .required("num", IntegerParser.integerParser())
                .flag(manager.flagBuilder("enum")
                        .withComponent(enumParser(TestEnum.class))
                        .build())
                .flag(manager.flagBuilder("static")
                        .build())
                .build());
        final String input = "flags 10 --enum foo ";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("--static"), suggestions);
    }

    @Test
    void Suggestions_NoFlagEntered_SuggestsFlagsAndAliases() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .flag(manager.flagBuilder("first").withAliases("f"))
                .flag(manager.flagBuilder("second").withAliases("s"))
                .flag(manager.flagBuilder("third").withAliases("t"))
                .build());

        final String input = "flags ";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("--first", "--second", "--third", "-f", "-s", "-t"), suggestions);
    }

    @Test
    void Suggestions_PresenceFlagEntered_SuggestsOtherPresenceFlags() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .flag(manager.flagBuilder("first").withAliases("f"))
                .flag(manager.flagBuilder("second").withAliases("s"))
                .flag(manager.flagBuilder("third").withAliases("t"))
                .build());

        final String input = "flags -f";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("--first", "-fs", "-ft", "-f"), suggestions);
    }

    @Test
    void Suggestions_MultiplePresenceFlagEntered_SuggestsOtherPresenceFlags() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .flag(manager.flagBuilder("first").withAliases("f"))
                .flag(manager.flagBuilder("second").withAliases("s"))
                .flag(manager.flagBuilder("third").withAliases("t"))
                .build());

        final String input = "flags -f -s";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("--second", "-st", "-s"), suggestions);
    }

    @Test
    void Suggestions_NonExistentFlagEntered_ListsAllFlags() {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("flags")
                .flag(manager.flagBuilder("first").withAliases("f"))
                .flag(manager.flagBuilder("second").withAliases("s"))
                .flag(manager.flagBuilder("third").withAliases("t"))
                .build());

        final String input = "flags --invalid ";

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        Assertions.assertEquals(suggestionList("--first", "--second", "--third", "-f", "-s", "-t"), suggestions);
    }

    @Test
    void testCompoundFlags() {
        final String input = "flags3 ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("--compound", "--presence", "--single", "-p"), suggestions);

        final String input2 = "flags3 --c";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("--compound"), suggestions2);

        final String input3 = "flags3 --compound ";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions3);

        final String input4 = "flags3 --compound 1";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(suggestionList("1", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"), suggestions4);

        final String input5 = "flags3 --compound 22 ";
        final List<? extends Suggestion> suggestions5 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input5).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions5);

        final String input6 = "flags3 --compound 22 1";
        final List<? extends Suggestion> suggestions6 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input6).list();
        Assertions.assertEquals(suggestionList("1", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"), suggestions6);

        /* We've typed compound already, so that flag should be omitted from the suggestions */
        final String input7 = "flags3 --compound 22 33 44 ";
        final List<? extends Suggestion> suggestions7 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input7).list();
        Assertions.assertEquals(suggestionList("--presence", "--single", "-p"), suggestions7);

        final String input8 = "flags3 --compound 22 33 44 --pres";
        final List<? extends Suggestion> suggestions8 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input8).list();
        Assertions.assertEquals(suggestionList("--presence"), suggestions8);

        final String input9 = "flags3 --compound 22 33 44 --presence ";
        final List<? extends Suggestion> suggestions9 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input9).list();
        Assertions.assertEquals(suggestionList("--single"), suggestions9);

        final String input10 = "flags3 --compound 22 33 44 --single ";
        final List<? extends Suggestion> suggestions10 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input10).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions10);
    }

    @Test
    void testNumbers() {
        final String input = "numbers ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions);
        final String input2 = "numbers 1";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("1", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"), suggestions2);
        final String input3 = "numbers -";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("-1", "-2", "-3", "-4", "-5", "-6", "-7", "-8", "-9"), suggestions3);
        final String input4 = "numbers -1";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(
                suggestionList("-1", "-10", "-11", "-12", "-13", "-14", "-15", "-16", "-17", "-18", "-19"),
                suggestions4
        );
        final String input5 = "numberswithmin ";
        final List<? extends Suggestion> suggestions5 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input5).list();
        Assertions.assertEquals(suggestionList("5", "6", "7", "8", "9"), suggestions5);

        final String input6 = "numbers 1 ";
        final List<? extends Suggestion> suggestions6 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input6).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions6);
    }

    @Test
    void testNumbersWithFollowingArguments() {
        final String input = "numberswithfollowingargument ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), suggestions);
        final String input2 = "numberswithfollowingargument 1";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("1", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"), suggestions2);
        final String input3 = "numberswithfollowingargument -";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("-1", "-2", "-3", "-4", "-5", "-6", "-7", "-8", "-9"), suggestions3);
        final String input4 = "numberswithfollowingargument -1";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(
                suggestionList("-1", "-10", "-11", "-12", "-13", "-14", "-15", "-16", "-17", "-18", "-19"),
                suggestions4
        );
    }

    @ParameterizedTest
    @MethodSource("testDurationsSource")
    void testDurations(final @NonNull String input, final @NonNull Iterable<@NonNull Suggestion> expectedSuggestions) {
        // Arrange
        this.manager = createManager();
        this.manager.command(manager.commandBuilder("duration").required("duration", durationParser()));

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        assertThat(suggestions).containsExactlyElementsIn(expectedSuggestions);
    }

    static @NonNull Stream<Arguments> testDurationsSource() {
        return Stream.of(
                arguments("duration ", suggestionList("1", "2", "3", "4", "5", "6", "7", "8", "9")),
                arguments("duration 5", suggestionList("5d", "5h", "5m", "5s")),
                arguments("duration 5s", Collections.emptyList()),
                arguments("duration 5s ", Collections.emptyList())
        );
    }

    @Test
    void testInvalidLiteralThenSpace() {
        final String input = "test o";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("one"), suggestions);
        final String input2 = "test o ";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions2);
        final String input3 = "test o abc123xyz";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions3);
    }

    @Test
    void testStringArgumentWithSuggestionProvider() {
        /*
         * [/partial] - should not match anything
         * [/partial ] - should show all possible suggestions unsorted
         * [/partial h] - should show all starting with 'h' (which is all) unsorted
         * [/partial he] - should show only those starting with he, unsorted
         * [/partial hey] - should show 'hey' and 'heya' (matches exactly and starts with)
         * [/partial hi] - should show only 'hi', it is the only one that matches exactly
         * [/partial b] - should show no suggestions, none match
         * [/partial hello ] - should show the literal following the argument (suggested)
         * [/partial bonjour ] - should show the literal following the argument (not suggested)
         */
        final String input = "partial";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions);
        final String input2 = "partial ";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("hi", "hey", "heya", "hai", "hello"), suggestions2);
        final String input3 = "partial h";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("hi", "hey", "heya", "hai", "hello"), suggestions3);
        final String input4 = "partial he";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(suggestionList("hey", "heya", "hello"), suggestions4);
        final String input5 = "partial hey";
        final List<? extends Suggestion> suggestions5 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input5).list();
        Assertions.assertEquals(suggestionList("hey", "heya"), suggestions5);
        final String input6 = "partial hi";
        final List<? extends Suggestion> suggestions6 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input6).list();
        Assertions.assertEquals(suggestionList("hi"), suggestions6);
        final String input7 = "partial b";
        final List<? extends Suggestion> suggestions7 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input7).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions7);
        final String input8 = "partial hello ";
        final List<? extends Suggestion> suggestions8 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input8).list();
        Assertions.assertEquals(suggestionList("literal"), suggestions8);
        final String input9 = "partial bonjour ";
        final List<? extends Suggestion> suggestions9 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input9).list();
        Assertions.assertEquals(suggestionList("literal"), suggestions9);
    }

    @Test
    void testLiteralWithVariable() {
        final String input = "literal_with_variable ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("vici", "veni", "vidi"), suggestions);
        final String input2 = "literal_with_variable v";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("vici", "veni", "vidi"), suggestions2);
        final String input3 = "literal_with_variable vi";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(suggestionList("vici", "vidi"), suggestions3);
        final String input4 = "literal_with_variable vidi";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(suggestionList("vidi"), suggestions4);
        final String input5 = "literal_with_variable vidi ";
        final List<? extends Suggestion> suggestions5 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input5).list();
        Assertions.assertEquals(suggestionList("now"), suggestions5);
        final String input6 = "literal_with_variable vici ";
        final List<? extends Suggestion> suggestions6 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input6).list();
        Assertions.assertEquals(suggestionList("later"), suggestions6);
    }

    @Test
    void testInvalidArgumentShouldNotCauseFurtherCompletion() {
        // pass preprocess
        final String input = "cmd_with_multiple_args 512 ";
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();
        Assertions.assertEquals(suggestionList("foo", "bar"), suggestions);
        final String input2 = "cmd_with_multiple_args 512 BAR ";
        final List<? extends Suggestion> suggestions2 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input2).list();
        Assertions.assertEquals(suggestionList("world"), suggestions2);
        /*final String input3 = "cmd_with_multiple_args test ";
        final List<? extends Suggestion> suggestions3 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input3).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions3);*/
        final String input4 = "cmd_with_multiple_args 512 f";
        final List<? extends Suggestion> suggestions4 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input4).list();
        Assertions.assertEquals(suggestionList("foo"), suggestions4);
        final String input5 = "cmd_with_multiple_args world f";
        final List<? extends Suggestion> suggestions5 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input5).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions5);
        // trigger preprocess fail
        final String input6 = "cmd_with_multiple_args 1024";
        final List<? extends Suggestion> suggestions6 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input6).list();
        Assertions.assertEquals(11, suggestions6.size());
        final String input7 = "cmd_with_multiple_args 1024 ";
        final List<? extends Suggestion> suggestions7 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input7).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions7);
        final String input8 = "cmd_with_multiple_args 1024 f";
        final List<? extends Suggestion> suggestions8 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input8).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions8);
        final String input9 = "cmd_with_multiple_args 1024 foo w";
        final List<? extends Suggestion> suggestions9 = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input9).list();
        Assertions.assertEquals(Collections.emptyList(), suggestions9);
    }

    @Test
    void testFlagYieldingGreedyStringFollowedByFlagArgument() {
        // Arrange
        this.manager = createManager();
        this.manager.command(
                this.manager.commandBuilder("command")
                        .required("string", greedyFlagYieldingStringParser(),
                                SuggestionProvider.blocking((c, i) -> suggestionList("hello")))
                        .flag(manager.flagBuilder("flag").withAliases("f").build())
                        .flag(manager.flagBuilder("flag2").build())
        );

        // Act
        final List<? extends Suggestion> suggestions1 = suggest(manager, "command ");
        final List<? extends Suggestion> suggestions2 = suggest(manager, "command hel");
        final List<? extends Suggestion> suggestions3 = suggest(manager, "command hello --");
        final List<? extends Suggestion> suggestions4 = suggest(manager, "command hello --f");
        final List<? extends Suggestion> suggestions5 = suggest(manager, "command hello -f");
        final List<? extends Suggestion> suggestions6 = suggest(manager, "command hello -");

        // Assert
        assertThat(suggestions1).containsExactlyElementsIn(suggestionList("hello"));
        assertThat(suggestions2).containsExactlyElementsIn(suggestionList("hello"));
        assertThat(suggestions3).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions4).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions5).containsExactlyElementsIn(suggestionList("--flag2", "--flag", "-f"));
        assertThat(suggestions6).isEmpty();
    }

    @Test
    void testFlagYieldingStringArrayFollowedByFlagArgument() {
        // Arrange
        this.manager = createManager();
        this.manager.command(
                this.manager.commandBuilder("command")
                        .required("array", flagYieldingStringArrayParser())
                        .flag(manager.flagBuilder("flag").withAliases("f").build())
                        .flag(manager.flagBuilder("flag2").build())
        );

        // Act
        final List<? extends Suggestion> suggestions1 = suggest(manager, "command ");
        final List<? extends Suggestion> suggestions2 = suggest(manager, "command hello");
        final List<? extends Suggestion> suggestions3 = suggest(manager, "command hello --");
        final List<? extends Suggestion> suggestions4 = suggest(manager, "command hello --f");
        final List<? extends Suggestion> suggestions5 = suggest(manager, "command hello -f");
        final List<? extends Suggestion> suggestions6 = suggest(manager, "command hello -");

        // Assert
        assertThat(suggestions1).isEmpty();
        assertThat(suggestions2).isEmpty();
        assertThat(suggestions3).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions4).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions5).containsExactlyElementsIn(suggestionList("--flag2", "--flag", "-f"));
        assertThat(suggestions6).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("testGreedyArgumentSuggestsAfterSpaceSource")
    void testGreedyArgumentSuggestsAfterSpace(
            final @NonNull String input,
            final @NonNull Iterable<@NonNull Suggestion> expectedSuggestions
    ) {
        // Arrange
        this.manager = createManager();
        this.manager.command(
                this.manager.commandBuilder("command")
                        .required("string", greedyStringParser(),
                                SuggestionProvider.blocking((c, i) -> suggestionList("hello world")))
        );
        this.manager.suggestionProcessor(
                new FilteringSuggestionProcessor<>(
                        FilteringSuggestionProcessor.Filter.<TestCommandSender>startsWith(true).and(
                                (ctx, s, in) ->
                                        StringUtils.trimBeforeLastSpace(s, in))));

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        assertThat(suggestions).containsExactlyElementsIn(expectedSuggestions);
    }

    static @NonNull Stream<Arguments> testGreedyArgumentSuggestsAfterSpaceSource() {
        return Stream.of(
                arguments("command ", suggestionList("hello world")),
                arguments("command hello", suggestionList("hello world")),
                arguments("command hello ", suggestionList("world")),
                arguments("command hello wo", suggestionList("world")),
                arguments("command hello world", suggestionList("world")),
                arguments("command hello world ", suggestionList())
        );
    }

    @Test
    void testFlagYieldingGreedyStringWithLiberalFlagArgument() {
        // Arrange
        this.manager = createManager();
        this.manager.settings().set(ManagerSetting.LIBERAL_FLAG_PARSING, true);
        this.manager.command(
                this.manager.commandBuilder("command")
                        .required("string", greedyFlagYieldingStringParser(),
                                SuggestionProvider.blocking((c, i) -> suggestionList("hello")))
                        .flag(manager.flagBuilder("flag").withAliases("f").build())
                        .flag(manager.flagBuilder("flag2").build())
        );

        // Act
        final List<? extends Suggestion> suggestions1 = suggest(manager, "command ");
        final List<? extends Suggestion> suggestions2 = suggest(manager, "command hel");
        final List<? extends Suggestion> suggestions3 = suggest(manager, "command hello --");
        final List<? extends Suggestion> suggestions4 = suggest(manager, "command hello --f");
        final List<? extends Suggestion> suggestions5 = suggest(manager, "command hello -f");
        final List<? extends Suggestion> suggestions6 = suggest(manager, "command hello -");

        // Assert
        assertThat(suggestions1).containsExactlyElementsIn(suggestionList("hello", "--flag", "--flag2", "-f"));
        assertThat(suggestions2).containsExactlyElementsIn(suggestionList("hello"));
        assertThat(suggestions3).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions4).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions5).containsExactlyElementsIn(suggestionList("--flag2", "--flag", "-f"));
        assertThat(suggestions6).isEmpty();
    }

    @Test
    void testFlagYieldingStringArrayWithLiberalFlagArgument() {
        // Arrange
        this.manager = createManager();
        this.manager.settings().set(ManagerSetting.LIBERAL_FLAG_PARSING, true);
        this.manager.command(
                this.manager.commandBuilder("command")
                        .required("array", flagYieldingStringArrayParser())
                        .flag(manager.flagBuilder("flag").withAliases("f").build())
                        .flag(manager.flagBuilder("flag2").build())
        );

        // Act
        final List<? extends Suggestion> suggestions1 = suggest(manager, "command ");
        final List<? extends Suggestion> suggestions2 = suggest(manager, "command hello");
        final List<? extends Suggestion> suggestions3 = suggest(manager, "command hello --");
        final List<? extends Suggestion> suggestions4 = suggest(manager, "command hello --f");
        final List<? extends Suggestion> suggestions5 = suggest(manager, "command hello -f");
        final List<? extends Suggestion> suggestions6 = suggest(manager, "command hello -");

        // Assert
        assertThat(suggestions1).containsExactlyElementsIn(suggestionList("--flag", "--flag2", "-f"));
        assertThat(suggestions2).isEmpty();
        assertThat(suggestions3).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions4).containsExactlyElementsIn(suggestionList("--flag", "--flag2"));
        assertThat(suggestions5).containsExactlyElementsIn(suggestionList("--flag2", "--flag", "-f"));
        assertThat(suggestions6).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("testTextFlagCompletionSource")
    void testTextFlagCompletion(final @NonNull String input, final @NonNull Iterable<@NonNull Suggestion> expectedSuggestions) {
        // Arrange
        this.manager = createManager();
        this.manager.settings().set(ManagerSetting.LIBERAL_FLAG_PARSING, true);
        this.manager.command(
                this.manager.commandBuilder("command")
                        .flag(manager.flagBuilder("flag").withAliases("f").withComponent(enumParser(TestEnum.class)).build())
                        .flag(manager.flagBuilder("flog").build())
        );

        // Act
        final List<? extends Suggestion> suggestions = this.manager.suggestionFactory().suggestImmediately(new TestCommandSender(), input).list();

        // Assert
        assertThat(suggestions).containsExactlyElementsIn(expectedSuggestions);
    }

    static @NonNull Stream<Arguments> testTextFlagCompletionSource() {
        return Stream.of(
                arguments("command ", suggestionList("--flag", "--flog", "-f")),
                arguments("command --", suggestionList("--flag", "--flog")),
                arguments("command --f", suggestionList("--flag", "--flog")),
                arguments("command --fla", suggestionList("--flag")),
                arguments("command -f", suggestionList("--flag", "--flog", "-f")),
                arguments("command -", suggestionList("--flag", "--flog", "-f")),
                arguments("command -f ", suggestionList("foo", "bar")),
                arguments("command -f b", suggestionList("bar"))
        );
    }

    @ParameterizedTest
    @MethodSource
    void respectsSenderTypeRequirement(
            final TestCommandSender sender,
            final String input,
            final List<Suggestion> expectedSuggestions
    ) {
        // Arrange
        this.manager = createTestManager();

        // 1)
        this.manager.command(this.manager.commandBuilder("test-specific-sender").senderType(SpecificSender.class));
        // 2)
        this.manager.command(this.manager.commandBuilder("literal").literal("test-specific-sender").senderType(SpecificSender.class));

        // Act
        final List<? extends Suggestion> list = this.manager.suggestionFactory().suggestImmediately(sender, input).list();

        // Assert
        assertThat(list).containsExactlyElementsIn(expectedSuggestions);
    }

    static Stream<Arguments> respectsSenderTypeRequirement() {
        return Stream.of(
                // 1)
                arguments(new TestCommandSender(), "test-", suggestionList()),
                arguments(new SpecificSender(), "test-", suggestionList("test-specific-sender")),
                // 2)
                arguments(new TestCommandSender(), "l", suggestionList()),
                arguments(new SpecificSender(), "l", suggestionList("literal")),
                arguments(new TestCommandSender(), "literal ", suggestionList()),
                arguments(new SpecificSender(), "literal ", suggestionList("test-specific-sender"))
        );
    }

    @ParameterizedTest
    @MethodSource
    void respectsPermissionRequirement(
            final TestCommandSender sender,
            final String input,
            final List<Suggestion> expectedSuggestions
    ) {
        // Arrange
        this.manager = createTestManager();

        // 1)
        this.manager.command(this.manager.commandBuilder("test-permitted").permission("some-permission"));
        // 2)
        this.manager.command(this.manager.commandBuilder("literal").literal("test-permitted").permission("some-permission"));

        // Act
        final List<? extends Suggestion> list = this.manager.suggestionFactory().suggestImmediately(sender, input).list();

        // Assert
        assertThat(list).containsExactlyElementsIn(expectedSuggestions);
    }

    static Stream<Arguments> respectsPermissionRequirement() {
        return Stream.of(
                // 1)
                arguments(new TestCommandSender(), "test-", suggestionList()),
                arguments(new TestCommandSender("some-permission"), "test-", suggestionList("test-permitted")),
                // 2)
                arguments(new TestCommandSender(), "l", suggestionList()),
                arguments(new TestCommandSender("some-permission"), "l", suggestionList("literal")),
                arguments(new TestCommandSender(), "literal ", suggestionList()),
                arguments(new TestCommandSender("some-permission"), "literal ", suggestionList("test-permitted"))
        );
    }

    private static CommandManager<TestCommandSender> createTestManager() {
        return new CommandManager<TestCommandSender>(
                ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()
        ) {
            @Override
            public boolean hasPermission(final TestCommandSender sender, final String permission) {
                return sender.hasPermisison(permission);
            }
        };
    }


    private List<? extends Suggestion> suggest(CommandManager<TestCommandSender> manager, String command) {
        return manager.suggestionFactory().suggestImmediately(new TestCommandSender(), command).list();
    }

    public enum TestEnum {
        FOO,
        BAR
    }

    static class SpecificSender extends TestCommandSender {

        @Override
        public String toString() {
            return "SpecificSender{permissions=" + this.permissions + "}";
        }
    }
}
