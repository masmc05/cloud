//
// MIT License
//
// Copyright (c) 2020 Alexander Söderberg & Contributors
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
package cloud.commandframework.arguments.standard;

import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NumberParseException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public final class IntegerArgument<C> extends CommandArgument<C, Integer> {

    private static final int MAX_SUGGESTIONS_INCREMENT = 10;
    private static final int NUMBER_SHIFT_MULTIPLIER = 10;

    private final int min;
    private final int max;

    private IntegerArgument(final boolean required,
                            @NonNull final String name,
                            final int min,
                            final int max,
                            final String defaultValue,
                            @Nullable final BiFunction<@NonNull CommandContext<C>, @NonNull String,
                                    @NonNull List<@NonNull String>> suggestionsProvider) {
        super(required, name, new IntegerParser<>(min, max), defaultValue, Integer.class, suggestionsProvider);
        this.min = min;
        this.max = max;
    }

    /**
     * Create a new builder
     *
     * @param name Name of the argument
     * @param <C>  Command sender type
     * @return Created builder
     */
    public static <C> @NonNull Builder<C> newBuilder(@NonNull final String name) {
        return new Builder<>(name);
    }

    /**
     * Create a new required command argument
     *
     * @param name Argument name
     * @param <C>  Command sender type
     * @return Created argument
     */
    public static <C> @NonNull CommandArgument<C, Integer> of(@NonNull final String name) {
        return IntegerArgument.<C>newBuilder(name).asRequired().build();
    }

    /**
     * Create a new optional command argument
     *
     * @param name Argument name
     * @param <C>  Command sender type
     * @return Created argument
     */
    public static <C> @NonNull CommandArgument<C, Integer> optional(@NonNull final String name) {
        return IntegerArgument.<C>newBuilder(name).asOptional().build();
    }

    /**
     * Create a new required command argument with a default value
     *
     * @param name       Argument name
     * @param defaultNum Default num
     * @param <C>        Command sender type
     * @return Created argument
     */
    public static <C> @NonNull CommandArgument<C, Integer> optional(@NonNull final String name,
                                                                    final int defaultNum) {
        return IntegerArgument.<C>newBuilder(name).asOptionalWithDefault(Integer.toString(defaultNum)).build();
    }

    /**
     * Get the minimum accepted integer that could have been parsed
     *
     * @return Minimum integer
     */
    public int getMin() {
        return this.min;
    }

    /**
     * Get the maximum accepted integer that could have been parsed
     *
     * @return Maximum integer
     */
    public int getMax() {
        return this.max;
    }

    public static final class Builder<C> extends CommandArgument.Builder<C, Integer> {

        private int min = Integer.MIN_VALUE;
        private int max = Integer.MAX_VALUE;

        protected Builder(@NonNull final String name) {
            super(Integer.class, name);
        }

        /**
         * Set a minimum value
         *
         * @param min Minimum value
         * @return Builder instance
         */
        public @NonNull Builder<C> withMin(final int min) {
            this.min = min;
            return this;
        }

        /**
         * Set a maximum value
         *
         * @param max Maximum value
         * @return Builder instance
         */
        public @NonNull Builder<C> withMax(final int max) {
            this.max = max;
            return this;
        }

        /**
         * Builder a new integer argument
         *
         * @return Constructed argument
         */
        @Override
        public @NonNull IntegerArgument<C> build() {
            return new IntegerArgument<>(this.isRequired(), this.getName(), this.min, this.max,
                                         this.getDefaultValue(), this.getSuggestionsProvider());
        }

    }

    public static final class IntegerParser<C> implements ArgumentParser<C, Integer> {

        private final int min;
        private final int max;

        /**
         * Construct a new integer parser
         *
         * @param min Minimum acceptable value
         * @param max Maximum acceptable value
         */
        public IntegerParser(final int min, final int max) {
            this.min = min;
            this.max = max;
        }

        static @NonNull List<@NonNull String> getSuggestions(final long min, final long max, @NonNull final String input) {
            if (input.isEmpty()) {
                return IntStream.range(0, MAX_SUGGESTIONS_INCREMENT).mapToObj(Integer::toString).collect(Collectors.toList());
            }
            try {
                final long inputNum = Long.parseLong(input);
                if (inputNum > max) {
                    return Collections.emptyList();
                } else {
                    final List<String> suggestions = new LinkedList<>();
                    suggestions.add(input); /* It's a valid number, so we suggest it */
                    for (int i = 0; i < MAX_SUGGESTIONS_INCREMENT
                            && (inputNum * NUMBER_SHIFT_MULTIPLIER) + i <= max; i++) {
                        suggestions.add(Long.toString((inputNum * NUMBER_SHIFT_MULTIPLIER) + i));
                    }
                    return suggestions;
                }
            } catch (final Exception ignored) {
                return Collections.emptyList();
            }
        }

        @Override
        public @NonNull ArgumentParseResult<Integer> parse(
                @NonNull final CommandContext<C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue) {
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NullPointerException("No input was provided"));
            }
            try {
                final int value = Integer.parseInt(input);
                if (value < this.min || value > this.max) {
                    return ArgumentParseResult.failure(new IntegerParseException(input, this.min, this.max));
                }
                inputQueue.remove();
                return ArgumentParseResult.success(value);
            } catch (final Exception e) {
                return ArgumentParseResult.failure(new IntegerParseException(input, this.min, this.max));
            }
        }

        /**
         * Get the minimum value accepted by this parser
         *
         * @return Min value
         */
        public int getMin() {
            return this.min;
        }

        /**
         * Get the maximum value accepted by this parser
         *
         * @return Max value
         */
        public int getMax() {
            return this.max;
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(@NonNull final CommandContext<C> commandContext,
                                                          @NonNull final String input) {
            return getSuggestions(this.min, this.max, input);
        }

    }


    public static final class IntegerParseException extends NumberParseException {

        /**
         * Construct a new integer parse exception
         *
         * @param input String input
         * @param min   Minimum value
         * @param max   Maximum value
         */
        public IntegerParseException(@NonNull final String input, final int min, final int max) {
            super(input, min, max);
        }

        @Override
        public boolean hasMin() {
            return this.getMin().intValue() != Integer.MIN_VALUE;
        }

        @Override
        public boolean hasMax() {
            return this.getMax().intValue() != Integer.MAX_VALUE;
        }

        @Override
        public @NonNull String getNumberType() {
            return "integer";
        }

    }

}
