class T {
  public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("net.minecraft.client.renderer.rendertype.RenderTypes");
    for (var m : c.getDeclaredMethods()) {
      if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
        System.out.println(m.toString());
      }
    }
  }
}
