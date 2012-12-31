#!/usr/bin/python

# Generates the C++ code for defining the AST classes. The classes for defining
# the Nodes are pretty much complete boilerplate. They are basically dumb
# data structures. This generates that boilerplate.
from os.path import dirname, join, realpath

magpie_dir = dirname(dirname(realpath(__file__)))
header_path = join(magpie_dir, 'src', 'Syntax', 'Ast.generated.h')

# Define the AST classes.
exprs = sorted({
    'And': [
        ('left',        'gc<Expr>'),
        ('right',       'gc<Expr>')],
    'Assign': [
        ('lvalue',      'gc<LValue>'),
        ('value',       'gc<Expr>')],
    'Async': [
        ('body',        'gc<Expr>'),
        ('resolved',    'ResolvedProcedure*')],
    'Bool': [
        ('value',       'bool')],
    'Break': [],
    'Call': [
        ('leftArg',     'gc<Expr>'),
        ('name',        'gc<String>'),
        ('rightArg',    'gc<Expr>'),
        ('resolved*',   'int')],
    'Catch': [
        ('body',        'gc<Expr>'),
        ('catches',     'Array<MatchClause>')],
    'Character': [
        ('value',       'unsigned int')],
    'DefClass': [
        ('name',         'gc<String>'),
        ('isNative',     'bool'),
        ('superclasses', 'Array<gc<Expr> >'),
        ('fields',       'Array<gc<ClassField> >'),
        ('resolved*',    'gc<ResolvedName>'),
        ('synthesizedMethods*', 'Array<gc<DefExpr> >')],
    'Def': [
        ('leftParam',   'gc<Pattern>'),
        ('name',        'gc<String>'),
        ('rightParam',  'gc<Pattern>'),
        ('value',       'gc<Pattern>'),
        ('body',        'gc<Expr>'),
        ('resolved',    'ResolvedProcedure*')],
    'Do': [
        ('body',        'gc<Expr>')],
    'Float': [
        ('value',       'double')],
    'Fn': [
        ('pattern',     'gc<Pattern>'),
        ('body',        'gc<Expr>'),
        ('resolved',    'ResolvedProcedure*')],
    'For': [
        ('pattern',     'gc<Pattern>'),
        ('iterator',    'gc<Expr>'),
        ('body',        'gc<Expr>')],
    'GetField': [
        ('index',       'int')],
    'If': [
        ('condition',   'gc<Expr>'),
        ('thenArm',     'gc<Expr>'),
        ('elseArm',     'gc<Expr>')],
    'Import': [
        ('name',        'gc<String>')],
    'Int': [
        ('value',       'int')],
    'Is': [
        ('value',       'gc<Expr>'),
        ('type',        'gc<Expr>')],
    'List': [
        ('elements',    'Array<gc<Expr> >')],
    'Match': [
        ('value',       'gc<Expr>'),
        ('cases',       'Array<MatchClause>')],
    'Name': [
        ('name',        'gc<String>'),
        ('resolved*',   'gc<ResolvedName>')],
    'Native': [
        ('name',        'gc<String>'),
        ('index*',      'int')],
    'Not': [
        ('value',       'gc<Expr>')],
    'Nothing': [],
    'Or': [
        ('left',        'gc<Expr>'),
        ('right',       'gc<Expr>')],
    'Record': [
        ('fields',      'Array<Field>')],
    'Return': [
        ('value',       'gc<Expr>')],
    'Sequence': [
        ('expressions', 'Array<gc<Expr> >')],
    'SetField': [
        ('index',       'int')],
    'String': [
        ('value',       'gc<String>')],
    'Throw': [
        ('value',       'gc<Expr>')],
    'Variable': [
        ('isMutable',   'bool'),
        ('pattern',     'gc<Pattern>'),
        ('value',       'gc<Expr>')],
    'While': [
        ('condition',   'gc<Expr>'),
        ('body',        'gc<Expr>')]
}.items())

patterns = sorted({
    'Record': [
        ('fields',      'Array<PatternField>')],
    'Type': [
        ('type',        'gc<Expr>')],
    'Value': [
        ('value',       'gc<Expr>')],
    'Variable': [
        ('name',        'gc<String>'),
        ('pattern',     'gc<Pattern>'),
        ('resolved*',   'gc<ResolvedName>')],
    'Wildcard': []
}.items())

lvalues = sorted({
    'Call': [
        ('call',        'gc<CallExpr>')],
    'Name': [
        ('name',        'gc<String>'),
        ('resolved*',   'gc<ResolvedName>')],
    'Record': [
        ('fields',      'Array<LValueField>')],
    'Wildcard': []
}.items())

HEADER = '''// Automatically generated by script/generate_ast.py.
// Do not hand-edit.

'''

REACH_METHOD = '''
  virtual void reach()
  {{
{0}  }}
'''

REACH_FIELD_ARRAY = '''
    for (int i = 0; i < {0}_.count(); i++)
    {{
        {0}_[i].name.reach();
        {0}_[i].value.reach();
    }}
'''

# TODO(bob): The "int arg" param is useless for the Def visitor. Should omit it.
BASE_CLASS = '''
class {0} : public Managed
{{
public:
  {0}(gc<SourcePos> pos)
  : pos_(pos)
  {{}}

  virtual ~{0}() {{}}

  // The visitor pattern.
  virtual void accept({0}Visitor& visitor, {2} arg) = 0;

  // Dynamic casts.
{1}
  gc<SourcePos> pos() const {{ return pos_; }}

private:
  gc<SourcePos> pos_;
}};
'''

# TODO(bob): The "int arg" param is useless for the Def visitor. Should omit it.
SUBCLASS = '''
class {0}{6} : public {6}
{{
public:
  {0}{6}(gc<SourcePos> pos{1})
  : {6}(pos){2}
  {{}}

  virtual void accept({6}Visitor& visitor, {7} arg)
  {{
    visitor.visit(*this, arg);
  }}

  virtual {0}{6}* as{0}{6}() {{ return this; }}

{3}{4}
  virtual void trace(std::ostream& out) const;

private:{5}
  NO_COPY({0}{6});
}};
'''

VISITOR_HEADER = '''
class {0}Visitor
{{
public:
  virtual ~{0}Visitor() {{}}

'''

VISITOR_FOOTER = '''
protected:
  {0}Visitor() {{}}

private:
  NO_COPY({0}Visitor);
}};
'''

num_types = 0

def main():
    # Create the AST header.
    with open(header_path, 'w') as file:
        file.write(HEADER)

        # Write the forward declarations.
        forwardDeclare(file, exprs, 'Expr')
        forwardDeclare(file, lvalues, 'LValue')
        forwardDeclare(file, patterns, 'Pattern')

        makeAst(file, 'Expr', 'int', exprs)
        makeAst(file, 'LValue', 'int', lvalues)
        makeAst(file, 'Pattern', 'int', patterns)

    print 'Created', num_types, 'types.'


def forwardDeclare(file, nodes, name):
    for className, fields in nodes:
        file.write('class {0}{1};\n'.format(className, name))


def makeAst(file, name, visitorParam, types):
    makeVisitor(file, name, visitorParam, types)
    makeBaseClass(file, name, visitorParam, types)
    for type, fields in types:
        makeClass(file, name, type, visitorParam, fields)


def makeVisitor(file, name, param, types):
    result = VISITOR_HEADER.format(name)

    for className, fields in types:
        result += ('  virtual void visit({1}{0}& node, {2} arg) = 0;\n'
            .format(name, className, param))

    file.write(result + VISITOR_FOOTER.format(name))


def makeClass(file, baseClass, className, visitorParam, fields):
    global num_types
    num_types += 1
    ctorParams = ''
    ctorArgs = ''
    accessors = ''
    memberVars = ''
    reachFields = ''
    for name, type in fields:
        # Whether or not the field has a setter.
        settable = False
        if name.endswith('*'):
            settable = True
            name = name[:-1]

        # Whether or not the field is a mutable value accessed by reference.
        mutable = False
        if type.endswith('*'):
            mutable = True
            type = type[:-1]

        if type.find('gc<') != -1:
            reachFields += '    ' + name + '_.reach();\n'
        if type.endswith('Field>'):
            reachFields += REACH_FIELD_ARRAY.format(name)

        if not settable and not mutable:
            ctorParams += ', '
            if type.startswith('Array'):
                # Array fields are passed to the constructor by reference.
                ctorParams += 'const ' + type + '&'
            else:
                ctorParams += type
            ctorParams += ' ' + name
            ctorArgs += ',\n    {0}_({0})'.format(name)
        else:
            if type == 'int':
                ctorArgs += ',\n    {0}_(-1)'.format(name)
            else:
                ctorArgs += ',\n    {0}_()'.format(name)

        if mutable:
            # Mutable fields are returned by reference.
            accessors += '  {1}& {0}() {{ return {0}_; }}\n'.format(name, type)
        elif type.startswith('Array'):
            # Accessors for arrays do not copy.
            accessors += '  const {1}& {0}() const {{ return {0}_; }}\n'.format(
                name, type)
        else:
            accessors += '  {1} {0}() const {{ return {0}_; }}\n'.format(
                name, type)

        # Include a setter too if it's settable.
        if settable:
            accessors += '  void set{2}({1} {0}) {{ {0}_ = {0}; }}\n'.format(
                name, type, name[0].upper() + name[1:])

        memberVars += '\n  {1} {0}_;'.format(name, type)

    reach = ''
    # Only generate a reach() method if the class contains a GC-ed field.
    if reachFields != '':
        reach = REACH_METHOD.format(reachFields)

    file.write(SUBCLASS.format(className, ctorParams, ctorArgs, accessors,
                               reach, memberVars, baseClass, visitorParam))

def makeBaseClass(file, name, visitorParam, types):
    casts = ''
    for subclass, fields in types:
        casts += '  virtual {1}{0}* as{1}{0}()'.format(name, subclass)
        casts += ' { return NULL; }\n'

    file.write(BASE_CLASS.format(name, casts, visitorParam))

main()
