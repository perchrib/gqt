import javafx.scene.control.TextField;

/**
 * Created by Johannes on 15.10.2015.
 */
public class NumberTextField extends TextField
{

    public NumberTextField(int defaultValue) {
        this.setText(Integer.toString(defaultValue));
    }

    @Override
    public void replaceText(int start, int end, String text)
    {
        if (validate(text))
        {
            super.replaceText(start, end, text);
        }

    }

    @Override
    public void replaceSelection(String text)
    {
        System.out.println("b");
        if (validate(text))
        {
            super.replaceSelection(text);
        }
    }

    private boolean validate(String text)
    {
        return ("".equals(text) || text.matches("[0-9]"));
    }

    public int getValue() {
        String val = this.getText();
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }

    }
}
