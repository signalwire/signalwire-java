package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class MathSkill implements SkillBase {

  /** Returns an empty hint list — this skill ships no example hints. */
  @Override
  public List<String> getHints() {
    return Collections.emptyList();
  }

  /** This skill has no custom parameters and returns the base schema. */
  @Override
  public Map<String, Object> getParameterSchema() {
    return SkillParams.base(supportsMultipleInstances(), getName());
  }

  @Override
  public String getName() {
    return "math";
  }

  @Override
  public String getDescription() {
    return "Perform basic mathematical calculations";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return false;
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    return true;
  }

  @Override
  public List<ToolDefinition> registerTools() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("type", "object");
    params.put(
        "properties",
        Map.of(
            "expression",
            Map.of(
                "type",
                "string",
                "description",
                "Mathematical expression to evaluate (e.g. 2+3, 10*5, 100/4)")));
    // No `required` — Python's math skill passes none (math/skill.py:33); the
    // handler returns a friendly prompt when expression is empty. Matching the
    // reference contract keeps SKILL-CONTRACT parity.

    ToolDefinition calc =
        new ToolDefinition(
            "calculate",
            "Perform a mathematical calculation with basic operations (+, -, *, /, %, **)",
            params,
            (args, raw) -> {
              String expr = (String) args.get("expression");
              if (expr == null || expr.isEmpty()) {
                return new FunctionResult("No expression provided");
              }
              try {
                double result = evaluateExpression(expr);
                if (result == Math.floor(result) && !Double.isInfinite(result)) {
                  return new FunctionResult("Result: " + (long) result);
                }
                return new FunctionResult("Result: " + result);
              } catch (Exception e) {
                return new FunctionResult("Error evaluating expression: " + e.getMessage());
              }
            });

    return List.of(calc);
  }

  /**
   * Safe expression evaluator - supports +, -, *, /, %, ** and parentheses. No arbitrary code
   * execution.
   */
  private double evaluateExpression(String expr) {
    // Validate input: only allow digits, operators, parentheses, spaces, dots
    String cleaned = expr.replaceAll("\\s+", "");
    if (!cleaned.matches("[0-9+\\-*/%.()^]+") && !cleaned.contains("**")) {
      throw new IllegalArgumentException("Invalid characters in expression");
    }
    // Replace ** with ^ for internal handling
    cleaned = cleaned.replace("**", "^");
    return parseExpression(cleaned, new int[] {0});
  }

  private double parseExpression(String expr, int[] pos) {
    double result = parseTerm(expr, pos);
    while (pos[0] < expr.length()) {
      char op = expr.charAt(pos[0]);
      if (op == '+' || op == '-') {
        pos[0]++;
        double term = parseTerm(expr, pos);
        result = op == '+' ? result + term : result - term;
      } else {
        break;
      }
    }
    return result;
  }

  private double parseTerm(String expr, int[] pos) {
    double result = parsePower(expr, pos);
    while (pos[0] < expr.length()) {
      char op = expr.charAt(pos[0]);
      if (op == '*' || op == '/' || op == '%') {
        pos[0]++;
        double factor = parsePower(expr, pos);
        if (op == '*') result *= factor;
        else if (op == '/') {
          if (factor == 0) throw new ArithmeticException("Division by zero");
          result /= factor;
        } else result %= factor;
      } else {
        break;
      }
    }
    return result;
  }

  private double parsePower(String expr, int[] pos) {
    double base = parseFactor(expr, pos);
    if (pos[0] < expr.length() && expr.charAt(pos[0]) == '^') {
      pos[0]++;
      double exponent = parsePower(expr, pos); // right-associative
      return Math.pow(base, exponent);
    }
    return base;
  }

  private double parseFactor(String expr, int[] pos) {
    if (pos[0] >= expr.length()) throw new IllegalArgumentException("Unexpected end of expression");
    char c = expr.charAt(pos[0]);
    if (c == '(') {
      pos[0]++;
      double result = parseExpression(expr, pos);
      if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') {
        pos[0]++;
      }
      return result;
    }
    if (c == '-') {
      pos[0]++;
      return -parseFactor(expr, pos);
    }
    // Parse number
    int start = pos[0];
    while (pos[0] < expr.length()
        && (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
      pos[0]++;
    }
    if (start == pos[0]) throw new IllegalArgumentException("Expected number at position " + start);
    return Double.parseDouble(expr.substring(start, pos[0]));
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Mathematical Calculations");
    section.put("body", "");
    section.put(
        "bullets",
        List.of(
            "Use calculate to perform mathematical operations",
            "Supports +, -, *, /, %, ** (exponentiation) and parentheses"));
    return List.of(section);
  }
}
