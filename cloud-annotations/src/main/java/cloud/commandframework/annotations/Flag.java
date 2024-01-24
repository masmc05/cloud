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
package cloud.commandframework.annotations;

import cloud.commandframework.parser.ParserRegistry;
import cloud.commandframework.parser.flag.CommandFlag;
import cloud.commandframework.suggestion.SuggestionProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Indicates that the parameter should be treated like a {@link CommandFlag}.
 * <ul>
 *     <li>If the parameter is a {@code boolean}, a presence flag will be created</li>
 *     <li>If the parameter is of any other type, a value flag will be created and the parser
 *     will resolve it in the same way that it would for an {@link Argument}</li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flag {

    /**
     * The flag name
     *
     * @return Flag name
     */
    @NonNull String value();

    /**
     * Flag aliases
     *
     * @return Aliases
     */
    @NonNull String[] aliases() default "";

    /**
     * Name of the parser. Leave empty to use
     * the default parser for the parameter type
     *
     * @return Parser name
     */
    @NonNull String parserName() default "";

    /**
     * Name of the suggestion provider to use. If the string is left empty, the default
     * provider for the argument parser will be used. Otherwise,
     * the {@link ParserRegistry} instance in the
     * {@link cloud.commandframework.CommandManager} will be queried for a matching suggestion provider.
     * <p>
     * For this to work, the suggestion needs to be registered in the parser registry. To do this, use
     * {@link ParserRegistry#registerSuggestionProvider(String, SuggestionProvider)}.
     * The registry instance can be retrieved using {@link cloud.commandframework.CommandManager#parserRegistry()}.
     *
     * @return The name of the suggestion provider, or {@code ""} if the default suggestion provider for the argument parser
     *         should be used instead
     */
    @NonNull String suggestions() default "";

    /**
     * The argument description
     *
     * @return Argument description
     */
    @NonNull String description() default "";

    /**
     * The flag permission
     *
     * @return Flag permission
     */
    @NonNull String permission() default "";

    /**
     * Whether the flag can be repeated.
     *
     * @return whether the flag can be repeated
     */
    @API(status = API.Status.STABLE)
    boolean repeatable() default false;
}
