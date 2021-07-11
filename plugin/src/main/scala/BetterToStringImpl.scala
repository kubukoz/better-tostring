package com.kubukoz

// Source-compatible core between 2.x and 3.x implementations

trait CompilerApi {
  type Tree
  type Clazz
  type Param
  type ParamName
  type Method
  type EnclosingObject

  def className(clazz: Clazz): String
  def isPackageOrPackageObject(enclosingObject: EnclosingObject): Boolean
  def enclosingObjectName(enclosingObject: EnclosingObject): String
  def params(clazz: Clazz): List[Param]
  def literalConstant(value: String): Tree

  def paramName(param: Param): ParamName
  def selectInThis(clazz: Clazz, name: ParamName): Tree
  def concat(l: Tree, r: Tree): Tree

  def createToString(clazz: Clazz, body: Tree): Method
  def addMethod(clazz: Clazz, method: Method): Clazz
  def methodNames(clazz: Clazz): List[String]
  def isCaseClass(clazz: Clazz): Boolean
  def isObject(clazz: Clazz): Boolean
}

trait BetterToStringImpl[+C <: CompilerApi] {
  val compilerApi: C

  def transformClass(
    clazz: compilerApi.Clazz,
    isNested: Boolean,
    enclosingObject: Option[compilerApi.EnclosingObject]
  ): compilerApi.Clazz

}

object BetterToStringImpl {

  def instance(
    api: CompilerApi
  ): BetterToStringImpl[api.type] =
    new BetterToStringImpl[api.type] {
      val compilerApi: api.type = api

      import api._

      def transformClass(
        clazz: Clazz,
        isNested: Boolean,
        enclosingObject: Option[EnclosingObject]
      ): Clazz = {
        val hasToString: Boolean = methodNames(clazz).contains("toString")

        val shouldModify = isCaseClass(clazz) && !isNested && !hasToString

        if (shouldModify) overrideToString(clazz, enclosingObject)
        else clazz
      }

      private def overrideToString(clazz: Clazz, enclosingObject: Option[EnclosingObject]): Clazz =
        addMethod(clazz, createToString(clazz, toStringImpl(clazz, enclosingObject)))

      private def toStringImpl(clazz: Clazz, enclosingObject: Option[EnclosingObject]): Tree = {
        val className = api.className(clazz)
        val parentPrefix = enclosingObject.filterNot(api.isPackageOrPackageObject).fold("")(api.enclosingObjectName(_) ++ ".")

        val namePart = literalConstant(parentPrefix ++ className)

        val paramListParts: List[Tree] = params(clazz).zipWithIndex.flatMap { case (v, index) =>
          val commaPrefix = if (index > 0) ", " else ""

          val name = paramName(v)

          List(
            literalConstant(commaPrefix ++ name.toString ++ " = "),
            selectInThis(clazz, name)
          )
        }

        val paramParts =
          if (api.isObject(clazz)) Nil
          else
            List(
              List(literalConstant("(")),
              paramListParts,
              List(literalConstant(")"))
            ).flatten

        val parts =
          namePart :: paramParts

        parts.reduceLeft(concat(_, _))
      }

    }

}
