# GluonJ

GluonJ is an extension to Java. It introduces a new class-like construct named a reviser.
A reviser extends the definition of an existing class like open classes of Ruby and the intertype declaration of AspectJ.
Although the declaration of a reviser is similar to that of a subclass, a reviser directly modifies
the definition of an existing class; it can add a new method and override a method in the target class.
A subclass, on the other hand, defines a new class with extension while the original class (i.e. the super class) remains.
