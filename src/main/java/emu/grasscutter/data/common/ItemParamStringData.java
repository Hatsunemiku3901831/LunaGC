package emu.grasscutter.data.common;

public class ItemParamStringData {
    private int id;
    private String count;

    public ItemParamStringData() {}

    public int getId() {
        return id;
    }

    public String getCount() {
        return count;
    }

    public ItemParamData toItemParamData() {
        String amount = count;
        if (amount.contains(";")) {
            String[] split = amount.split(";");
            amount = split[split.length - 1];
        } else if (amount.contains(".")) {
            return new ItemParamData(id, (int) Math.ceil(Double.parseDouble(amount)));
        }
        return new ItemParamData(id, Integer.parseInt(amount));
    }
}
