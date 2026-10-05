//! A tiny, sandboxed s-expression evaluator for the NEU-REPO tree layouts
//! (`hotmlayout.json`, `hotflayout.json`). The layouts encode perk costs like
//! `(pow (+ level 2) 3.05)` and a prelude of helper `defun`s.
//!
//! The evaluator is deliberately limited: no I/O, bounded recursion depth and a step
//! budget, so malformed or hostile data can never hang or crash the service.

use std::collections::HashMap;
use std::fmt;

use thiserror::Error;

#[derive(Debug, Clone, PartialEq)]
pub enum Value {
    Num(f64),
    Str(String),
    Bool(bool),
    /// `:KEYWORD` or bare unresolved symbol used as a value (e.g. `MITHRIL`).
    Sym(String),
    List(Vec<Value>),
    Nil,
}

impl Value {
    pub fn as_num(&self) -> Option<f64> {
        match self {
            Value::Num(n) => Some(*n),
            Value::Str(s) => s.trim().parse().ok(),
            Value::Bool(b) => Some(if *b { 1.0 } else { 0.0 }),
            _ => None,
        }
    }
    pub fn as_text(&self) -> String {
        match self {
            Value::Num(n) => {
                if n.fract() == 0.0 && n.abs() < 1e15 { format!("{}", *n as i64) } else { format!("{n}") }
            }
            Value::Str(s) | Value::Sym(s) => s.clone(),
            Value::Bool(b) => b.to_string(),
            Value::List(_) => "<list>".into(),
            Value::Nil => String::new(),
        }
    }
    fn truthy(&self) -> bool {
        match self {
            Value::Bool(b) => *b,
            Value::Nil => false,
            Value::Num(n) => *n != 0.0,
            _ => true,
        }
    }
}

impl fmt::Display for Value {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.as_text())
    }
}

#[derive(Debug, Error, PartialEq)]
pub enum ExprError {
    #[error("parse error: {0}")]
    Parse(String),
    #[error("eval error: {0}")]
    Eval(String),
    #[error("evaluation budget exceeded")]
    Budget,
}

#[derive(Debug, Clone, PartialEq)]
enum Node {
    Atom(String),
    Str(String),
    List(Vec<Node>),
}

fn tokenize(src: &str) -> Result<Vec<String>, ExprError> {
    let mut out = Vec::new();
    let chars: Vec<char> = src.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        match c {
            '(' | ')' => {
                out.push(c.to_string());
                i += 1;
            }
            '"' => {
                let mut s = String::from("\"");
                i += 1;
                while i < chars.len() && chars[i] != '"' {
                    s.push(chars[i]);
                    i += 1;
                }
                if i >= chars.len() {
                    return Err(ExprError::Parse("unterminated string".into()));
                }
                i += 1;
                s.push('"');
                out.push(s);
            }
            c if c.is_whitespace() => i += 1,
            _ => {
                let mut s = String::new();
                while i < chars.len() && !chars[i].is_whitespace() && chars[i] != '(' && chars[i] != ')' {
                    s.push(chars[i]);
                    i += 1;
                }
                out.push(s);
            }
        }
        if out.len() > 10_000 {
            return Err(ExprError::Parse("expression too long".into()));
        }
    }
    Ok(out)
}

fn parse_tokens(tokens: &[String], pos: &mut usize, depth: usize) -> Result<Node, ExprError> {
    if depth > 64 {
        return Err(ExprError::Parse("nesting too deep".into()));
    }
    let tok = tokens.get(*pos).ok_or_else(|| ExprError::Parse("unexpected end".into()))?;
    *pos += 1;
    match tok.as_str() {
        "(" => {
            let mut items = Vec::new();
            loop {
                match tokens.get(*pos) {
                    None => return Err(ExprError::Parse("missing )".into())),
                    Some(t) if t == ")" => {
                        *pos += 1;
                        return Ok(Node::List(items));
                    }
                    Some(_) => items.push(parse_tokens(tokens, pos, depth + 1)?),
                }
            }
        }
        ")" => Err(ExprError::Parse("unexpected )".into())),
        t if t.starts_with('"') => Ok(Node::Str(t.trim_matches('"').to_string())),
        t => Ok(Node::Atom(t.to_string())),
    }
}

fn parse(src: &str) -> Result<Node, ExprError> {
    let tokens = tokenize(src)?;
    if tokens.is_empty() {
        return Err(ExprError::Parse("empty expression".into()));
    }
    let mut pos = 0;
    let node = parse_tokens(&tokens, &mut pos, 0)?;
    if pos != tokens.len() {
        return Err(ExprError::Parse("trailing tokens".into()));
    }
    Ok(node)
}

#[derive(Debug, Clone)]
struct Function {
    params: Vec<String>,
    body: Node,
}

/// Evaluation environment: prelude functions + variables.
#[derive(Debug, Clone, Default)]
pub struct Env {
    functions: HashMap<String, Function>,
    vars: HashMap<String, Value>,
}

impl Env {
    pub fn with_prelude(prelude: &[String]) -> Result<Self, ExprError> {
        let mut env = Env::default();
        for line in prelude {
            let node = parse(line)?;
            if let Node::List(items) = &node {
                if let [Node::Atom(kw), Node::Atom(name), Node::List(params), body] = items.as_slice() {
                    if kw == "defun" {
                        let params = params
                            .iter()
                            .map(|p| match p {
                                Node::Atom(a) => Ok(a.clone()),
                                _ => Err(ExprError::Parse("bad param".into())),
                            })
                            .collect::<Result<Vec<_>, _>>()?;
                        env.functions.insert(name.clone(), Function { params, body: body.clone() });
                        continue;
                    }
                }
            }
            return Err(ExprError::Parse(format!("unsupported prelude form: {line}")));
        }
        Ok(env)
    }

    pub fn set(&mut self, name: &str, value: Value) -> &mut Self {
        self.vars.insert(name.to_string(), value);
        self
    }

    pub fn set_num(&mut self, name: &str, value: f64) -> &mut Self {
        self.set(name, Value::Num(value))
    }

    pub fn eval(&self, src: &str) -> Result<Value, ExprError> {
        let trimmed = src.trim();
        if trimmed.is_empty() {
            return Ok(Value::Nil);
        }
        let node = parse(trimmed)?;
        let mut budget = 100_000usize;
        self.eval_node(&node, &self.vars, &mut budget, 0)
    }

    pub fn eval_num(&self, src: &str) -> Result<Option<f64>, ExprError> {
        Ok(self.eval(src)?.as_num())
    }

    fn eval_node(&self, node: &Node, scope: &HashMap<String, Value>, budget: &mut usize, depth: usize) -> Result<Value, ExprError> {
        if *budget == 0 || depth > 200 {
            return Err(ExprError::Budget);
        }
        *budget -= 1;
        match node {
            Node::Str(s) => Ok(Value::Str(s.clone())),
            Node::Atom(a) => {
                if let Ok(n) = a.parse::<f64>() {
                    return Ok(Value::Num(n));
                }
                match a.as_str() {
                    "true" => return Ok(Value::Bool(true)),
                    "false" => return Ok(Value::Bool(false)),
                    "nil" => return Ok(Value::Nil),
                    _ => {}
                }
                if let Some(k) = a.strip_prefix(':') {
                    return Ok(Value::Sym(k.to_string()));
                }
                Ok(scope.get(a).cloned().unwrap_or_else(|| Value::Sym(a.clone())))
            }
            Node::List(items) => {
                let Some(Node::Atom(head)) = items.first() else {
                    return Err(ExprError::Eval("call head must be a symbol".into()));
                };
                let args = &items[1..];
                if head == "if" {
                    if args.len() != 3 {
                        return Err(ExprError::Eval("if takes 3 args".into()));
                    }
                    let c = self.eval_node(&args[0], scope, budget, depth + 1)?;
                    let branch = if c.truthy() { &args[1] } else { &args[2] };
                    return self.eval_node(branch, scope, budget, depth + 1);
                }
                let vals = args
                    .iter()
                    .map(|a| self.eval_node(a, scope, budget, depth + 1))
                    .collect::<Result<Vec<_>, _>>()?;
                self.apply(head, vals, budget, depth)
            }
        }
    }

    fn apply(&self, head: &str, vals: Vec<Value>, budget: &mut usize, depth: usize) -> Result<Value, ExprError> {
        let nums = || -> Result<Vec<f64>, ExprError> {
            vals.iter().map(|v| v.as_num().ok_or_else(|| ExprError::Eval(format!("{head}: non-numeric argument {v}")))).collect()
        };
        let num = |x: f64| -> Result<Value, ExprError> {
            if x.is_finite() { Ok(Value::Num(x)) } else { Err(ExprError::Eval(format!("{head}: non-finite result"))) }
        };
        match head {
            "+" => num(nums()?.iter().sum()),
            "*" => num(nums()?.iter().product()),
            "-" => {
                let n = nums()?;
                match n.as_slice() {
                    [] => Err(ExprError::Eval("- needs args".into())),
                    [x] => num(-x),
                    [x, rest @ ..] => num(rest.iter().fold(*x, |a, b| a - b)),
                }
            }
            "/" => {
                let n = nums()?;
                match n.as_slice() {
                    [a, b] if *b != 0.0 => num(a / b),
                    _ => Err(ExprError::Eval("/ needs 2 args and non-zero divisor".into())),
                }
            }
            "pow" => {
                let n = nums()?;
                match n.as_slice() {
                    [a, b] => num(a.powf(*b)),
                    _ => Err(ExprError::Eval("pow takes 2 args".into())),
                }
            }
            "round" | "floor" | "ceil" | "abs" => {
                let n = nums()?;
                let [x] = n.as_slice() else { return Err(ExprError::Eval(format!("{head} takes 1 arg"))) };
                num(match head {
                    "round" => x.round(),
                    "floor" => x.floor(),
                    "ceil" => x.ceil(),
                    _ => x.abs(),
                })
            }
            "lt" | "gt" | "le" | "ge" => {
                let n = nums()?;
                let [a, b] = n.as_slice() else { return Err(ExprError::Eval(format!("{head} takes 2 args"))) };
                Ok(Value::Bool(match head {
                    "lt" => a < b,
                    "gt" => a > b,
                    "le" => a <= b,
                    _ => a >= b,
                }))
            }
            "=" => {
                let [a, b] = vals.as_slice() else { return Err(ExprError::Eval("= takes 2 args".into())) };
                Ok(Value::Bool(match (a.as_num(), b.as_num()) {
                    (Some(x), Some(y)) => x == y,
                    _ => a.as_text() == b.as_text(),
                }))
            }
            "list.new" => Ok(Value::List(vals)),
            "list.at" => {
                let [Value::List(l), idx] = vals.as_slice() else { return Err(ExprError::Eval("list.at takes (list, idx)".into())) };
                let i = idx.as_num().ok_or_else(|| ExprError::Eval("list.at index".into()))?;
                if i < 0.0 {
                    return Ok(Value::Nil);
                }
                Ok(l.get(i as usize).cloned().unwrap_or(Value::Nil))
            }
            "concat" | "str" => Ok(Value::Str(vals.iter().map(Value::as_text).collect())),
            name => {
                let f = self.functions.get(name).ok_or_else(|| ExprError::Eval(format!("unknown function {name}")))?;
                if f.params.len() != vals.len() {
                    return Err(ExprError::Eval(format!("{name}: expected {} args", f.params.len())));
                }
                let mut scope = self.vars.clone();
                for (p, v) in f.params.iter().zip(vals) {
                    scope.insert(p.clone(), v);
                }
                self.eval_node(&f.body, &scope, budget, depth + 1)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn prelude() -> Vec<String> {
        vec![
            "(defun min (l r) (if (lt l r) l r))".into(),
            "(defun max (l r) (if (lt l r) r l))".into(),
            "(defun round-decimals (number places) (/ (round (* number (pow 10 places))) (pow 10 places)))".into(),
        ]
    }

    #[test]
    fn hotm_costs() {
        let mut env = Env::with_prelude(&prelude()).unwrap();
        env.set_num("level", 1.0);
        assert_eq!(env.eval_num("(pow (+ level 2) 3)").unwrap(), Some(27.0));
        env.set_num("level", 3.0);
        let v = env.eval("(list.at (list.new 0 50000 100000 200000 300000) level)").unwrap();
        assert_eq!(v.as_num(), Some(200000.0));
        let p = env.eval("(if (lt level 4) MITHRIL (if (lt level 8) GEMSTONE GLACITE))").unwrap();
        assert_eq!(p.as_text(), "MITHRIL");
        assert_eq!(env.eval_num("(min 3 7)").unwrap(), Some(3.0));
        assert_eq!(env.eval_num("(round-decimals 1.23456 2)").unwrap(), Some(1.23));
        env.set_num("potm", 1.0);
        assert_eq!(env.eval("(if (lt potm 2) \"200\" \"250\")").unwrap().as_text(), "200");
    }

    #[test]
    fn hostile_input_does_not_panic() {
        let env = Env::with_prelude(&prelude()).unwrap();
        assert!(env.eval("(((((").is_err());
        assert!(env.eval("(/ 1 0)").is_err());
        assert!(env.eval("(unknown 1)").is_err());
        assert!(env.eval("(pow 10 1000)").is_err());
        let recursive = Env::with_prelude(&["(defun f (x) (f x))".to_string()]).unwrap();
        assert_eq!(recursive.eval("(f 1)"), Err(ExprError::Budget));
    }
}
